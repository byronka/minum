package atqa.web;

import java.util.List;

public class ByteUtils {


    public static byte[] byteListToArray(List<Byte> result) {
        final var resultArray = new byte[result.size()];
        for(int i = 0; i < result.size(); i++) {
            resultArray[i] = result.get(i);
        }
        return resultArray;
    }
}
