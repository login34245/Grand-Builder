package dev.grandbuilder.item;

import dev.grandbuilder.build.StructureSelectionManager;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

public class StructureSelectorItem extends Item {
	public StructureSelectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer player) {
			StructureSelectionManager.setSecond(player, context.getClickedPos());
		}
		return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(
		ItemStack stack,
		Item.TooltipContext context,
		TooltipDisplay tooltipDisplay,
		Consumer<Component> textAdder,
		TooltipFlag flag
	) {
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_selector.line1"));
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_selector.line2"));
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_selector.line3"));
	}
}
