import vx.viewer.Terrain;

/**
 * Laboratório de conteúdo — sonda do relevo das vitrines (sem OpenGL).
 *
 * Imprime, em JSON, a altura do chão do relevo legado da E (T1, o mesmo {@code Terrain.e()} que o viewer desenha nas
 * composições {@code layout=}) numa grade de passo {@code PASSO} u, de −24 a +24 u. O {@code tools/conteudo/vitrines.py}
 * usa a grade para assentar cada construção (altura do patamar sob a pegada, desnível) sem chutar {@code dy}.
 *
 *   java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/GroundProbe.java 0.25 > output/conteudo/relevo.json
 */
public class GroundProbe {
    public static void main(String[] a) {
        float step = a.length > 0 ? Float.parseFloat(a[0]) : 0.25f;
        Terrain t = Terrain.e();
        int n = Math.round(48f / step) + 1;
        StringBuilder sb = new StringBuilder("{\"passo\": ").append(step).append(", \"x0\": -24, \"z0\": -24, \"n\": ").append(n).append(", \"h\": [");
        for (int iz = 0; iz < n; iz++) {
            sb.append(iz == 0 ? "[" : ",\n[");
            for (int ix = 0; ix < n; ix++) {
                float x = -24f + ix * step, z = -24f + iz * step;
                sb.append(ix == 0 ? "" : ",").append(String.format(java.util.Locale.ROOT, "%.3f", t.groundAt(x, z)));
            }
            sb.append("]");
        }
        System.out.println(sb.append("]}"));
    }
}
