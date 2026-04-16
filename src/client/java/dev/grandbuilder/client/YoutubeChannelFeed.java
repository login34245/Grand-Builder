package dev.grandbuilder.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class YoutubeChannelFeed {
	public static final String CHANNEL_URL = "https://www.youtube.com/@Login34245";

	private static final String CHANNEL_VIDEOS_URL = CHANNEL_URL + "/videos";
	private static final String CHANNEL_SHORTS_URL = CHANNEL_URL + "/shorts";
	private static final String CHANNEL_STREAMS_URL = CHANNEL_URL + "/streams";
	private static final long SUCCESS_CACHE_MS = 60L * 1000L;
	private static final long FAILURE_CACHE_MS = 20L * 1000L;
	private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("\"channelId\":\"(UC[\\w-]{22,28})\"");
	private static final Pattern LIVE_VIDEO_PATTERN_A = Pattern.compile(
		"\"videoId\":\"([A-Za-z0-9_-]{11})\"(?:(?!\"videoId\").){0,1800}?\"isLiveNow\":true",
		Pattern.DOTALL
	);
	private static final Pattern LIVE_VIDEO_PATTERN_B = Pattern.compile(
		"\"isLiveNow\":true(?:(?!\"isLiveNow\").){0,1800}?\"videoId\":\"([A-Za-z0-9_-]{11})\"",
		Pattern.DOTALL
	);
	private static final Pattern VIDEO_RENDERER_PATTERN = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"");
	private static final Pattern SHORTS_URL_PATTERN = Pattern.compile("\"url\":\"\\\\/shorts\\\\/([A-Za-z0-9_-]{11})\"");
	private static final Pattern GENERIC_VIDEO_ID_PATTERN = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"");
	private static final Pattern TITLE_RUN_PATTERN = Pattern.compile("\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]", Pattern.DOTALL);
	private static final Pattern TITLE_SIMPLE_PATTERN = Pattern.compile("\"title\":\\{\"simpleText\":\"(.*?)\"\\}", Pattern.DOTALL);
	private static final Pattern HEADLINE_SIMPLE_PATTERN = Pattern.compile("\"headline\":\\{\"simpleText\":\"(.*?)\"\\}", Pattern.DOTALL);
	private static final Pattern META_OG_TITLE_PATTERN = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern UPLOAD_DATE_PATTERN = Pattern.compile("\"uploadDate\":\"([^\"]+)\"");
	private static final Pattern DATE_PUBLISHED_META_PATTERN = Pattern.compile("<meta\\s+itemprop=\"datePublished\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "GrandBuilder-YouTube-Feed");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

	private static volatile Snapshot snapshot = Snapshot.loading();
	private static volatile long nextRefreshMs = 0L;

	private YoutubeChannelFeed() {
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static void forceRefresh() {
		nextRefreshMs = 0L;
		requestRefreshIfNeeded();
	}

	public static void requestRefreshIfNeeded() {
		long now = System.currentTimeMillis();
		if (now < nextRefreshMs || !FETCHING.compareAndSet(false, true)) {
			return;
		}

		EXECUTOR.execute(() -> {
			try {
				Snapshot fetched = fetchSnapshot();
				snapshot = fetched;
				nextRefreshMs = System.currentTimeMillis() + (fetched.isReady() ? SUCCESS_CACHE_MS : FAILURE_CACHE_MS);
			} finally {
				FETCHING.set(false);
			}
		});
	}

	private static Snapshot fetchSnapshot() {
		try {
			String streamsPage = readText(CHANNEL_STREAMS_URL);
			VideoCandidate liveCandidate = parseLiveFromStreamsPage(streamsPage);
			if (liveCandidate != null) {
				VideoCandidate resolvedLive = enrichCandidateFromVideoPage(liveCandidate);
				String title = resolvedLive.title().isBlank() ? "Live stream" : resolvedLive.title();
				return Snapshot.live(title, resolvedLive.url());
			}

			VideoCandidate videoCandidate = null;
			VideoCandidate shortsCandidate = null;

			try {
				String videosPage = readText(CHANNEL_VIDEOS_URL);
				videoCandidate = parseLatestFromVideosPage(videosPage);
			} catch (Exception ignored) {
				videoCandidate = null;
			}

			try {
				String shortsPage = readText(CHANNEL_SHORTS_URL);
				shortsCandidate = parseLatestFromShortsPage(shortsPage);
			} catch (Exception ignored) {
				shortsCandidate = null;
			}

			VideoCandidate resolvedVideo = enrichCandidateFromVideoPage(videoCandidate);
			VideoCandidate resolvedShort = enrichCandidateFromVideoPage(shortsCandidate);
			VideoCandidate newest = chooseNewest(resolvedVideo, resolvedShort);
			if (newest != null) {
				return Snapshot.ready(newest.title(), newest.url());
			}

			String channelPage = readText(CHANNEL_URL);
			String channelId = extractChannelId(channelPage);
			if (!channelId.isEmpty()) {
				String feedXml = readText("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId);
				LatestVideo latestFromFeed = parseLatestFromFeed(feedXml);
				if (latestFromFeed != null) {
					return Snapshot.ready(latestFromFeed.title(), latestFromFeed.url());
				}
			}
		} catch (Exception ignored) {
			// Fallback handled below.
		}
		return Snapshot.error();
	}

	private static VideoCandidate chooseNewest(VideoCandidate first, VideoCandidate second) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}

		if (first.publishedAtMs() > 0 && second.publishedAtMs() > 0) {
			return first.publishedAtMs() >= second.publishedAtMs() ? first : second;
		}
		if (first.publishedAtMs() > 0) {
			return first;
		}
		if (second.publishedAtMs() > 0) {
			return second;
		}
		return first;
	}

	private static VideoCandidate enrichCandidateFromVideoPage(VideoCandidate candidate) {
		if (candidate == null) {
			return null;
		}

		try {
			String page = readText(candidate.url());
			String pageTitle = extractMetaTitle(page);
			long publishedAtMs = extractPublishedAtMs(page);
			String title = pageTitle.isBlank() ? candidate.title() : pageTitle;
			return new VideoCandidate(
				candidate.id(),
				candidate.url(),
				normalizeTitle(title, candidate.shortForm()),
				candidate.shortForm(),
				publishedAtMs
			);
		} catch (Exception ignored) {
			return candidate;
		}
	}

	private static VideoCandidate parseLiveFromStreamsPage(String streamsPage) {
		Matcher matcherA = LIVE_VIDEO_PATTERN_A.matcher(streamsPage);
		int liveStart = -1;
		String liveId = "";
		if (matcherA.find()) {
			liveStart = matcherA.start();
			liveId = matcherA.group(1);
		} else {
			Matcher matcherB = LIVE_VIDEO_PATTERN_B.matcher(streamsPage);
			if (matcherB.find()) {
				liveStart = matcherB.start();
				liveId = matcherB.group(1);
			}
		}
		if (liveId.isBlank()) {
			return null;
		}

		String title = extractTitleNearIndex(streamsPage, liveStart);
		String url = "https://www.youtube.com/watch?v=" + liveId;
		return new VideoCandidate(liveId, url, normalizeTitle(title, false), false, 0L);
	}

	private static VideoCandidate parseLatestFromVideosPage(String videosPage) {
		Matcher matcher = VIDEO_RENDERER_PATTERN.matcher(videosPage);
		if (!matcher.find()) {
			return null;
		}
		String id = matcher.group(1);
		String title = extractTitleNearIndex(videosPage, matcher.start());
		return new VideoCandidate(id, "https://www.youtube.com/watch?v=" + id, normalizeTitle(title, false), false, 0L);
	}

	private static VideoCandidate parseLatestFromShortsPage(String shortsPage) {
		Matcher urlMatcher = SHORTS_URL_PATTERN.matcher(shortsPage);
		if (urlMatcher.find()) {
			String id = urlMatcher.group(1);
			String title = extractTitleNearIndex(shortsPage, urlMatcher.start());
			return new VideoCandidate(id, "https://www.youtube.com/shorts/" + id, normalizeTitle(title, true), true, 0L);
		}

		Matcher idMatcher = GENERIC_VIDEO_ID_PATTERN.matcher(shortsPage);
		if (!idMatcher.find()) {
			return null;
		}
		String id = idMatcher.group(1);
		String title = extractTitleNearIndex(shortsPage, idMatcher.start());
		return new VideoCandidate(id, "https://www.youtube.com/shorts/" + id, normalizeTitle(title, true), true, 0L);
	}

	private static String extractTitleNearIndex(String page, int anchorIndex) {
		if (anchorIndex < 0 || anchorIndex >= page.length()) {
			return "";
		}
		int from = Math.max(0, anchorIndex - 300);
		int to = Math.min(page.length(), anchorIndex + 3200);
		String window = page.substring(from, to);

		Matcher runTitle = TITLE_RUN_PATTERN.matcher(window);
		if (runTitle.find()) {
			return decodeJsonString(runTitle.group(1));
		}
		Matcher simpleTitle = TITLE_SIMPLE_PATTERN.matcher(window);
		if (simpleTitle.find()) {
			return decodeJsonString(simpleTitle.group(1));
		}
		Matcher headlineTitle = HEADLINE_SIMPLE_PATTERN.matcher(window);
		if (headlineTitle.find()) {
			return decodeJsonString(headlineTitle.group(1));
		}
		return "";
	}

	private static String extractMetaTitle(String html) {
		Matcher matcher = META_OG_TITLE_PATTERN.matcher(html);
		if (!matcher.find()) {
			return "";
		}
		return decodeHtmlEntities(matcher.group(1));
	}

	private static long extractPublishedAtMs(String html) {
		Matcher uploadDateMatcher = UPLOAD_DATE_PATTERN.matcher(html);
		if (uploadDateMatcher.find()) {
			long parsed = parseDateToEpochMillis(uploadDateMatcher.group(1));
			if (parsed > 0L) {
				return parsed;
			}
		}

		Matcher datePublishedMatcher = DATE_PUBLISHED_META_PATTERN.matcher(html);
		if (datePublishedMatcher.find()) {
			return parseDateToEpochMillis(datePublishedMatcher.group(1));
		}
		return 0L;
	}

	private static long parseDateToEpochMillis(String value) {
		if (value == null || value.isBlank()) {
			return 0L;
		}
		String normalized = value.trim();
		try {
			if (normalized.length() <= 10) {
				return LocalDate.parse(normalized).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
			}
			return Instant.parse(normalized).toEpochMilli();
		} catch (DateTimeParseException ignored) {
			try {
				return LocalDate.parse(normalized.substring(0, 10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
			} catch (Exception ignoredAgain) {
				return 0L;
			}
		}
	}

	private static String extractChannelId(String channelPage) {
		Matcher matcher = CHANNEL_ID_PATTERN.matcher(channelPage);
		return matcher.find() ? matcher.group(1) : "";
	}

	private static LatestVideo parseLatestFromFeed(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setExpandEntityReferences(false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(xml)));
		NodeList entries = document.getElementsByTagName("entry");
		if (entries.getLength() == 0 || !(entries.item(0) instanceof Element entry)) {
			return null;
		}

		String title = firstText(entry, "title");
		String link = firstLink(entry);
		if (link.isBlank()) {
			String videoId = firstText(entry, "yt:videoId");
			if (videoId.isBlank()) {
				videoId = firstText(entry, "videoId");
			}
			if (!videoId.isBlank()) {
				link = "https://www.youtube.com/watch?v=" + videoId;
			}
		}

		if (title.isBlank()) {
			title = "Latest upload";
		}
		if (link.isBlank()) {
			link = CHANNEL_URL;
		}
		return new LatestVideo(title, link);
	}

	private static String firstText(Element element, String tag) {
		NodeList nodes = element.getElementsByTagName(tag);
		if (nodes.getLength() == 0) {
			return "";
		}
		String text = nodes.item(0).getTextContent();
		return text == null ? "" : text.trim();
	}

	private static String firstLink(Element entry) {
		NodeList nodes = entry.getElementsByTagName("link");
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (!(node instanceof Element element)) {
				continue;
			}
			String rel = element.getAttribute("rel");
			String href = element.getAttribute("href");
			if (href == null || href.isBlank()) {
				continue;
			}
			if ("alternate".equalsIgnoreCase(rel) || rel.isBlank()) {
				return href;
			}
		}
		return "";
	}

	private static String decodeHtmlEntities(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return raw
			.replace("&amp;", "&")
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.trim();
	}

	private static String normalizeTitle(String title, boolean shortForm) {
		if (title == null || title.isBlank()) {
			return shortForm ? "Latest short" : "Latest upload";
		}
		return title.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private static String decodeJsonString(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return raw
			.replace("\\u0026", "&")
			.replace("\\u003d", "=")
			.replace("\\u003c", "<")
			.replace("\\u003e", ">")
			.replace("\\\"", "\"")
			.replace("\\/", "/")
			.replace("\\n", " ")
			.replace("\\r", " ")
			.trim();
	}

	private static String readText(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(8000);
		connection.setReadTimeout(8000);
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (GrandBuilder)");
		connection.setRequestProperty("Accept-Language", Locale.US.toLanguageTag());

		int code = connection.getResponseCode();
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " for " + url);
		}

		StringBuilder builder = new StringBuilder(4096);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
		}
		return builder.toString();
	}

	private record LatestVideo(String title, String url) {
	}

	private record VideoCandidate(String id, String url, String title, boolean shortForm, long publishedAtMs) {
	}

	public record Snapshot(State state, String latestTitle, String latestVideoUrl) {
		public static Snapshot loading() {
			return new Snapshot(State.LOADING, "", "");
		}

		public static Snapshot ready(String latestTitle, String latestVideoUrl) {
			return new Snapshot(State.READY, latestTitle, latestVideoUrl);
		}

		public static Snapshot live(String latestTitle, String latestVideoUrl) {
			return new Snapshot(State.LIVE, latestTitle, latestVideoUrl);
		}

		public static Snapshot error() {
			return new Snapshot(State.ERROR, "", "");
		}

		public boolean isReady() {
			return state == State.READY || state == State.LIVE;
		}
	}

	public enum State {
		LOADING,
		READY,
		LIVE,
		ERROR
	}
}
