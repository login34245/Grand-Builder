package dev.grandbuilder.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class StructureCoreItem extends Item {
	public StructureCoreItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(
		ItemStack stack,
		Item.TooltipContext context,
		TooltipDisplay tooltipDisplay,
		Consumer<Component> textAdder,
		TooltipFlag flag
	) {
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_core.line1"));
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_core.line2"));
		textAdder.accept(Component.translatable("tooltip.grand_builder.structure_core.line3"));
	}
}
