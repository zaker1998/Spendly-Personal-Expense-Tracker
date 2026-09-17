package com.spendly.config;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * Prefixes that belong to the server, not to the client-side router.
     *
     * <p>The SPA fallback is registered on {@code /**}, so before this list
     * existed a request to a mistyped API path found no controller, fell through
     * to the resource handler and came back as {@code index.html} with status
     * 200. A client expecting JSON got a page of HTML and no indication that the
     * endpoint does not exist. Returning null here lets the request finish as a
     * normal 404 from {@code GlobalExceptionHandler}.
     */
    private static final List<String> SERVER_PREFIXES = List.of(
            "api/",
            "actuator/",
            "v3/api-docs",
            "api-docs",
            "swagger-ui"
    );

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isServerPath(resourcePath)) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }

    /** {@code resourcePath} arrives without a leading slash. */
    private static boolean isServerPath(String resourcePath) {
        return SERVER_PREFIXES.stream().anyMatch(resourcePath::startsWith);
    }
}
