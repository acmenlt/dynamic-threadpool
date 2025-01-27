package cn.hippo4j.server.config;

import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author niejian9001@163.com
 * @desc https://blog.csdn.net/Hosea_star/article/details/108589768
 * @projectName hippo4j
 * @package cn.hippo4j.server.config
 * @date 2022/12/9 17:02
 */
@Configuration
public class HttpServletConfig {
    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> {
            connector.setProperty("relaxedQueryChars", "|{}[](),/:;<=>?@[\\]{}\\");
            connector.setProperty("relaxedPathChars", "|{}[](),/:;<=>?@[\\]{}\\");
            connector.setProperty("rejectIllegalHeader", "false");
        });

        return factory;
    }

}
