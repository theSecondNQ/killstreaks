package net.killstreakcod.render;

import java.util.Arrays;

/**
 * Repack adjacent source triangles without changing their diagonal, winding or attributes.
 * 重新组合相邻的源三角形，保留原对角线、顶点绕序及属性。
 */
public final class ImportedQuadIndices {
    private ImportedQuadIndices() {}

    static int[] pack(int[] triangles) {
        int[] result = new int[triangles.length / 3 * 4];
        int out = 0;
        for (int i = 0; i + 2 < triangles.length;) {
            boolean paired = false;
            if (i + 5 < triangles.length)
                for (int edge = 0; edge < 3 && !paired; edge++) {
                    int a = triangles[i + edge], b = triangles[i + (edge + 1) % 3], c = triangles[i + (edge + 2) % 3];
                    if (a == b || b == c || c == a)
                        continue;
                    for (int other = 0; other < 3; other++) {
                        int d = triangles[i + 3 + (other + 2) % 3];
                        if (triangles[i + 3 + other] != b || triangles[i + 3 + (other + 1) % 3] != a || d == a || d == b
                                || d == c)
                            continue;
                        // QUADS expands to (b,c,a), (a,d,b): precisely the two source triangles.
                        // 中文：QUADS 会展开为 (b,c,a)、(a,d,b)，恰好对应两个原始三角形。
                        result[out++] = b;
                        result[out++] = c;
                        result[out++] = a;
                        result[out++] = d;
                        i += 6;
                        paired = true;
                        break;
                    }
                }
            if (!paired) {
                result[out++] = triangles[i];
                result[out++] = triangles[i + 1];
                result[out++] = triangles[i + 2];
                result[out++] = triangles[i + 2];
                i += 3;
            }
        }
        return out == result.length ? result : Arrays.copyOf(result, out);
    }
}
