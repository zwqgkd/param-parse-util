package jar;

import com.ksyun.train.entity.Pod;
import com.ksyun.train.util.ParamParseUtil;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws Exception {
        Pod pod= ParamParseUtil.parse(Pod.class, "src/main/resources/test.yaml");
    }
}
