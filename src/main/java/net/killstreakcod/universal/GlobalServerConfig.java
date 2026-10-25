package net.killstreakcod.universal;

import java.nio.file.Path;
import java.util.function.Function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge 保留 SERVER 注册与登录同步，只把本模组规则文件的读取、监视和卸载定位到全局 config。 */
public final class GlobalServerConfig extends ModConfig {
    private static final ConfigFileTypeHandler GLOBAL_HANDLER = new ConfigFileTypeHandler() {
        @Override
        public Function<ModConfig, CommentedFileConfig> reader(Path worldConfig) {
            return super.reader(FMLPaths.CONFIGDIR.get());
        }

        @Override
        public void unload(Path worldConfig, ModConfig config) {
            super.unload(FMLPaths.CONFIGDIR.get(), config);
        }
    };

    public GlobalServerConfig(ModContainer container) {
        super(Type.SERVER, StreakServerConfig.SPEC, container, StreakServerConfig.FILE_NAME);
    }

    @Override
    public ConfigFileTypeHandler getHandler() {
        return GLOBAL_HANDLER;
    }
}
