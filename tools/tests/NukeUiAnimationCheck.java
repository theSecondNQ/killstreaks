import net.killstreakcod.render.MgbCountdownClock;
import net.killstreakcod.render.NukeUiAnimation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.google.gson.JsonParser;

/** Native endpoint and authoritative clock checks. 中文：原生位置终点和服务端计时检查。 */
public final class NukeUiAnimationCheck {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of(arguments[0]))).getAsJsonObject();
        var animation = new NukeUiAnimation(data);
        equal(animation.elements().size(), 25);
        equal(animation.duration(), 10000);
        check(!animation.frame("WarningLeft", 149).visible(), "Warning became visible early");
        check(animation.frame("WarningLeft", 150).visible(), "Warning did not become visible");
        check(animation.frame("WarningLeft", 0).image().equals("hud_splash_caution_icon"), "Constructor skull leaked");
        bounds(animation, "WarningLeft", 700, 580, 8, 60, 60);
        bounds(animation, "WarningRight", 700, 1276, 8, 60, 60);
        bounds(animation, "TacticalNukeText", 700, 707.5F, 11, 505, 50);
        bounds(animation, "CountdownElement", 700, 902, 73, 100, 28);
        bounds(animation, "Gamertag", 700, 1672, 9.5F, 234, 28);
        bounds(animation, "Message", 700, 1712, 38, 194, 21);
        bounds(animation, "BackerStrip", 0, 910, 0, 100, 104);
        bounds(animation, "BackerStrip", 730, -960, 0, 3840, 104);
        bounds(animation, "MainBacker", 300, -960, 0, 3840, 70);
        check(!animation.frame("CountdownElement", 99).visible(), "Timer became visible early");
        check(animation.frame("CountdownElement", 100).visible(), "Timer did not become visible");
        check(!animation.frame("Gamertag", 699).visible(), "Caller became visible early");
        check(animation.frame("Gamertag", 700).visible(), "Caller did not become visible");
        check(animation.frame("Accent5", 149).image().equals("hud_splash_parallelogram"), "Material changed early");
        check(animation.frame("Accent5", 150).image().equals("hud_splash_parallelogram_background"), "Material did not switch");
        equal(animation.frame("WarningLeft", 150).rgb(), 0xec4444);
        equal(animation.frame("WarningLeft", 700).rgb(), 0x9f3737);
        equal(animation.frame("Line1", 5000).bounds().width(), 483);
        equal(animation.frame("Line2", 5000).bounds().width(), 483);
        equal(animation.opacity(9800), 1);
        equal(animation.opacity(9925), .5F);
        equal(animation.opacity(10050), 0);
        Set<String> images = data.getAsJsonObject("images").keySet();
        for (int time = 0; time < 10100; time += 37)
            for (String element : animation.elements()) {
                var frame = animation.frame(element, time);
                check(Float.isFinite(frame.alpha()) && frame.alpha() >= 0 && frame.alpha() <= 1,
                        "Invalid alpha: " + element);
                check(Float.isFinite(frame.bounds().x()) && Float.isFinite(frame.bounds().y())
                        && frame.bounds().width() >= 0 && frame.bounds().height() >= 0, "Invalid bounds: " + element);
                check(!frame.visible() || frame.image().isEmpty() || images.contains(frame.image()),
                        "Unresolved visible material: " + frame.image());
            }
        clock();
        System.out.println("PASS: " + checks + " native keyframe/layout/material and server-clock checks");
    }

    private static void clock() {
        var clock = new MgbCountdownClock();
        check(clock.synchronize(2000, 200, 300), "Initial snapshot rejected");
        equal(clock.remaining(300, 0), 200);
        equal(clock.remaining(350, 0), 150);
        equal(clock.elapsedMilliseconds(350, 0), 2500);
        check(clock.synchronize(2050, 150, 350), "Updated snapshot rejected");
        check(!clock.synchronize(2040, 160, 360), "Out-of-order snapshot accepted");
        check(!clock.synchronize(2050, 150, 360), "Duplicate snapshot restarted the clock");
        equal(clock.remaining(360, 0), 140);
        check(clock.synchronize(2060, 180, 360), "Authoritative end-time correction rejected");
        equal(clock.remaining(360, 0), 180);
        clock.reset();
        check(clock.synchronize(9000, 40, 100), "Late-join snapshot rejected");
        equal(clock.elapsedMilliseconds(100, 0), 8000);
        equal(clock.remaining(120, .5F), 19.5F);
        equal(clock.remaining(160, 0), 0);
        clock.reset();
        equal(clock.remaining(160, 0), 0);
        check(clock.synchronize(0, 200, 0), "New strike retained old clock");
    }

    private static void bounds(NukeUiAnimation animation, String element, float time,
            float x, float y, float width, float height) {
        var bounds = animation.frame(element, time).bounds();
        equal(bounds.x(), x);
        equal(bounds.y(), y);
        equal(bounds.width(), width);
        equal(bounds.height(), height);
    }

    private static void equal(float actual, float expected) {
        check(Math.abs(actual - expected) <= .001F, "Expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }
}
