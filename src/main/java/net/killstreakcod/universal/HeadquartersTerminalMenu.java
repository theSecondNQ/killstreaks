package net.killstreakcod.universal;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Server-owned terminal session; validity follows the base block and interaction distance.
 * 服务端管理终端会话；底座被拆除或玩家离开交互距离后，菜单正常关闭。
 */
public final class HeadquartersTerminalMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private long nextAction;

    public HeadquartersTerminalMenu(int id, Inventory inventory) {
        this(id, ContainerLevelAccess.NULL);
    }

    public HeadquartersTerminalMenu(int id, ContainerLevelAccess access) {
        super(KillstreakContent.HEADQUARTERS_TERMINAL_MENU.get(), id);
        this.access = access;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, KillstreakContent.HEADQUARTERS_TERMINAL.get());
    }

    boolean acceptAction(long now) {
        if (now < nextAction)
            return false;
        nextAction = now + 5;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
