package org.endeavourhealth.enterprise.core;

import com.google.common.base.Charsets;

import java.io.IOException;
import java.net.URL;

public class Resources {

    public static String getResourceAsString(String url) throws IOException {
        URL urlItem = com.google.common.io.Resources.getResource("foo.txt");
        String text = com.google.common.io.Resources.toString(urlItem, Charsets.UTF_8);
        return text;
    }
}
