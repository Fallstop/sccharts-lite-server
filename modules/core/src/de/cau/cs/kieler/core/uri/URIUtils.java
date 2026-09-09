/*******************************************************************************
 * Copyright (c) 2007, 2010 BMW Car IT, Technische Universitaet Muenchen, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * BMW Car IT - Initial API and implementation
 * Technische Universitaet Muenchen - Major refactoring and extension
 * Johannes Faltermeier - Extension
 * Alexander Schulz-Rosengarten - Adjustments for KIELER project
 * sccharts-lite - Removed the Eclipse workspace and OSGi bundle lookups
 *******************************************************************************/
package de.cau.cs.kieler.core.uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;

/**
 * Conversions between EMF URIs, Java files and URLs. This build has no Eclipse workspace or
 * OSGi bundles, so platform:/resource URIs cannot be resolved and platform:/plugin URIs are
 * looked up on the classpath.
 */
public final class URIUtils {

    private URIUtils() {
    }

    /** Get an EMF URI for a file name. */
    public static URI getURI(String fileName) {
        return getURI(new File(fileName));
    }

    /** Get an EMF URI for a Java file. */
    public static URI getURI(File file) {
        return URI.createFileURI(file.getAbsolutePath());
    }

    /** Get a Java URL for an EMF URI; plug-in URIs are resolved against the classpath. */
    public static URL getURL(URI uri) {
        try {
            if (uri.isPlatformPlugin()) {
                return ClassLoader.getSystemResource(String.join("/", uri.segmentsList().subList(2, uri.segmentCount())));
            }
            return new URL(uri.toString());
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    /** Get an EMF URI from a Java URL. */
    public static URI getURI(URL url) {
        try {
            return URI.createURI(url.toURI().toString());
        } catch (final URISyntaxException e) {
            return null;
        }
    }

    /** Replace the file extension of a URI. */
    public static URI replaceExtension(URI uri, String ext) {
        return uri.trimFileExtension().appendFileExtension(ext);
    }

    /** Get the Java file behind a URI, or null when the URI does not denote a file on disk. */
    public static File getJavaFile(URI uri) {
        if (uri.isPlatformPlugin()) {
            URL url = getURL(uri);
            if (url == null || !"file".equals(url.getProtocol())) {
                return null;
            }
            try {
                return new File(url.toURI());
            } catch (final URISyntaxException e) {
                return null;
            }
        }
        if (uri.isPlatform() || uri.toFileString() == null) {
            return null;
        }
        return new File(uri.toFileString());
    }

    /** Get an input stream from the given URI, or null if no stream could be created. */
    public static InputStream getInputStream(URI uri) {
        try {
            return new ExtensibleURIConverterImpl().createInputStream(uri);
        } catch (final IOException ex) {
            return null;
        }
    }

    /** Get an output stream from the given URI, or null if no stream could be created. */
    public static OutputStream getOutputStream(URI uri) {
        try {
            return new ExtensibleURIConverterImpl().createOutputStream(uri);
        } catch (final IOException ex) {
            return null;
        }
    }

    /** Get the relative path of a URI with respect to another URI. */
    public static URI getRelativePath(URI uri, URI relativeTo) {
        return uri.deresolve(relativeTo, true, true, true);
    }
}
