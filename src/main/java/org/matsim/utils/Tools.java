package org.matsim.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Tools {
    public static void downloadFile(String fileUrl, String savePath) throws IOException {
        InputStream in = new URL(fileUrl).openStream();
        Files.copy(in, Paths.get(savePath), StandardCopyOption.REPLACE_EXISTING);
    }
}
