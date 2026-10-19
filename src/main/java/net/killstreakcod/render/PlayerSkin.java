package net.killstreakcod.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Local presentation data; skin selection never changes the player's server profile. */
public record PlayerSkin(ResourceLocation texture, String textureUrl, ResourceLocation capeTexture,
        ResourceLocation elytraTexture, Model model, boolean secure) {
    public enum Model { WIDE, SLIM }

    public static PlayerSkin current(AbstractClientPlayer player) {
        return new PlayerSkin(player.getSkinTextureLocation(), null, player.getCloakTextureLocation(),
                player.getElytraTextureLocation(), player.getModelName().equals("slim") ? Model.SLIM : Model.WIDE, false);
    }
}
