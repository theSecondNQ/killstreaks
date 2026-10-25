import net.killstreakcod.render.EarnedSplashAnimation;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;

/** Native boundary checks against the runtime sampler. 中文：核对运行时采样器的原生关键帧边界。 */
public final class EarnedSplashAnimationCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var animation = new EarnedSplashAnimation(data);
        close(animation.displayTime(), 2000);
        close(animation.totalTime(), 2600);
        close(animation.messageDelay(), 300);
        close(animation.top(), 200);
        var icon = animation.bounds("splash.StreakIcon", 1000);
        close(icon.x(), 725);
        close(icon.y(), -3.5F);
        close(icon.width(), 135);
        close(icon.height(), 135);
        close(animation.value("splash.StreakIcon", "Scale", 0, 0), 2);
        close(animation.value("splash.StreakIcon", "Scale", 25, 0), 1.5F);
        close(animation.value("splash.StreakIcon", "Scale", 50, 0), 0);
        close(animation.value("content.MainText", "Alpha", 199, 0), 0);
        close(animation.value("content.MainText", "Alpha", 200, 0), 1);
        close(animation.value("content.HintText", "Alpha", 350, 0), 0);
        close(animation.value("content.HintText", "Alpha", 370, 0), .8F);
        close(animation.value("content.HintText", "Alpha", 390, 0), 0);
        close(animation.value("content.HintText", "Alpha", 450, 0), .8F);
        close(animation.value("content.MainText", "Alpha", 2050, 0), .25F);
        close(animation.value("content.MainText", "Alpha", 2100, 0), 0);
        close(animation.value("content.MainText", "Alpha", 2200, 0), 1);
        close(animation.value("content.MainText", "Alpha", 2300, 0), 0);
        close(animation.value("content.UpperBacker", "Alpha", 1000, 0), .85F);
        close(animation.value("content.UpperBacker", "RGB", 500, 0), 0x368947);
        close(animation.value("content.UpperBacker", "Alpha", 2400, 0), .85F);
        close(animation.bounds("content.Divider", 200).height(), 0);
        close(animation.bounds("content.Divider", 300).height(), 70);
        close(animation.bounds("content.Divider", 2300).height(), 0);
        if (!animation.visible(2399) || animation.visible(2400) || animation.visible(-1))
            throw new AssertionError("Native Hide callback boundary differs");
        checks++;
        if (data.getAsJsonObject("rewards").size() != 20)
            throw new AssertionError("Expected 19 selectable rewards and MGB");
        checks++;
        // 倒序和重复采样不应改变轨道状态，避免掉帧或重绘使动画跳变。
        for (int time = 2500; time >= 0; time -= 5) {
            float a = animation.value("content.HintText", "Alpha", time, 0);
            animation.value("content.HintText", "Alpha", 450, 0);
            close(animation.value("content.HintText", "Alpha", time, 0), a);
        }
        System.out.println("PASS: " + checks + " native timing/layout and repeatability checks");
    }

    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > .001F)
            throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }
}
