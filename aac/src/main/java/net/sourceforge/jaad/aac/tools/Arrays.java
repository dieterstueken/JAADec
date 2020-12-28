package net.sourceforge.jaad.aac.tools;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 27.12.20
 * Time: 18:59
 */
public class Arrays {

    public static boolean[] copyOf(boolean[] array) {
        return array==null ? null : java.util.Arrays.copyOf(array, array.length);
    }

    public static int[] copyOf(int[] array) {
        return array==null ? null : java.util.Arrays.copyOf(array, array.length);
    }

    public static float[] copyOf(float[] array) {
        return array==null ? null : java.util.Arrays.copyOf(array, array.length);
    }
}
