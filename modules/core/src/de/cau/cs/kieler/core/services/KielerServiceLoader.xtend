/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 * 
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2019 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.core.services

import java.util.ServiceLoader

/**
 * ServiceLoader adapted to the KIELER project.
 * 
 * @author als
 */
class KielerServiceLoader {

    /**
     * Loads and instantiates all implementing classes of the given service registered via '/META-INF/services/'.
     * <br>
     * If executed with a running eclipse platform the implementations will be loaded from the Bundles, otherwise the
     * standard ServiceLoader is used.
     * 
     * @param  service
     *         The interface or abstract class representing the service.
     * 
     * @return An iterable with instances of all implementing classes.
     */
    static def <S> Iterable<S> load(Class<S> service) {
        // sccharts-lite runs without OSGi: implementations are always found through META-INF/services.
        return ServiceLoader.load(service)
    }
}
