package com.sashank.paraspa.config;

import com.sashank.paraspa.storage.LocalDiskStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.concurrent.TimeUnit;

/** Serves locally stored uploads at /files/**. Not active when another storage backend is selected. */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    @Autowired
    private LocalDiskStorageService storageService;

    /**
     * Path.toUri() only ends with "/" when the directory already exists, and Spring treats a location
     * without the slash as a single file. The uploads folder does not exist yet on a fresh install,
     * so always add the slash ourselves.
     */
    static String toResourceLocation(java.nio.file.Path root) {
        String uri = root.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(LocalDiskStorageService.URL_PREFIX + "**")
                .addResourceLocations(toResourceLocation(storageService.getRoot()))
                // file names contain a random id and are never overwritten, so they can be cached
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
