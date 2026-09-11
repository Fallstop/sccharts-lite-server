/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://www.informatik.uni-kiel.de/rtsys/kieler/
 * 
 * Copyright 2017 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 * See the file epl-v10.html for the license text.
 */
 package de.cau.cs.kieler.scg.processors.analyzer

import de.cau.cs.kieler.scg.diagnostics.Loops
import de.cau.cs.kieler.core.diagnostics.Issue
import com.google.inject.Inject
import de.cau.cs.kieler.core.properties.IProperty
import de.cau.cs.kieler.core.properties.Property
import de.cau.cs.kieler.kicool.compilation.InplaceProcessor
import de.cau.cs.kieler.scg.Depth
import de.cau.cs.kieler.scg.Entry
import de.cau.cs.kieler.scg.Exit
import de.cau.cs.kieler.scg.Fork
import de.cau.cs.kieler.scg.Node
import de.cau.cs.kieler.scg.SCGraphs
import de.cau.cs.kieler.scg.Surface
import de.cau.cs.kieler.scg.extensions.SCGControlFlowExtensions
import de.cau.cs.kieler.scg.extensions.SCGDependencyExtensions
import de.cau.cs.kieler.scg.extensions.SCGMethodExtensions
import de.cau.cs.kieler.scg.extensions.SCGSequentialForkExtensions
import java.util.Set

import static extension de.cau.cs.kieler.core.diagnostics.TracedEcoreUtil.*

/** 
 * @author ssm
 * @kieler.design 2017-11-01 proposed 
 * @kieler.rating 2017-11-01 proposed yellow
 */
class LoopAnalyzerV2 extends InplaceProcessor<SCGraphs> {
	
	@Inject extension SCGControlFlowExtensions
	@Inject extension SCGDependencyExtensions
    @Inject extension SCGMethodExtensions
    @Inject extension SCGSequentialForkExtensions
	@Inject extension TarjanSCC
	
	public static val IProperty<Boolean> LOOP_ANALYZER_ENABLED = 
	   new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.enabled", true)
    public static val IProperty<Boolean> LOOP_ANALYZER_CONSIDER_ALL_DEPENDENCIES = 
       new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.considerAllDependencies", false)
    public static val IProperty<Boolean> LOOP_ANALYZER_FINAL_REGIONS_ENABLED = 
       new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.finalRegions.enabled", true)
       
       
    public static val IProperty<LoopData> LOOP_DATA = 
        new Property<LoopData>("de.cau.cs.kieler.scg.processors.loopAnalyzer.data", null)	
    public static val IProperty<Boolean> LOOP_DATA_PERSISTENT = 
        new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.data.persistent", false)
           
    public static val IProperty<Boolean> ERROR_ON_INSTANTANEOUS_LOOP = 
        new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.errorOnInstantaneousLoop", false)
    public static val IProperty<Boolean> WARNING_ON_INSTANTANEOUS_LOOP = 
        new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.warningOnInstantaneousLoop", false)
    public static val IProperty<Boolean> INFO_ON_INSTANTANEOUS_LOOP = 
        new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.infoOnInstantaneousLoop", false)
        
    public static val IProperty<Integer> LOOP_ANALYZER_MAX_STRIPPED_NODES = 
        new Property<Integer>("de.cau.cs.kieler.scg.processors.loopAnalyzer.maxStrippedNodes", 100)
    public static val IProperty<Boolean> LOOP_ANALYZER_STOP_AFTER_FIRST_LOOP = 
        new Property<Boolean>("de.cau.cs.kieler.scg.processors.loopAnalyzer.stopAfterFirstLoop", false)
    public static val IProperty<Integer> LOOP_ANALYZER_ADJACENT_DEPTH =
        new Property<Integer>("de.cau.cs.kieler.scg.processors.loopAnalyzer.adjacentDepth", 1)
	
    override getId() {
        "de.cau.cs.kieler.scg.processors.loopAnalyzerV2"
    }
    
    override getName() {
        "Loop Analyzer V2"
    }
    
    override process() {
        val model = getModel
        val loopData = new LoopData(environment.getProperty(LOOP_DATA_PERSISTENT))
        val threadData = environment.getProperty(ThreadAnalyzer.THREAD_DATA)
        if (threadData === null) {
            environment.warnings.add("This processor requires thread information, but no thread data was found.")
            return;
        }
        environment.setProperty(LOOP_DATA, loopData)

        if (!environment.getProperty(LOOP_ANALYZER_ENABLED)) return;
        
        for (scg : model.scgs.ignoreMethods) {
            scg.findSCCs(loopData, environment.getProperty(LOOP_ANALYZER_CONSIDER_ALL_DEPENDENCIES))
        }
               
        if (!loopData.criticalNodes.empty) {
            // Diagnostics: the loops are reported as located, explained issues; the stripped loop model stays
            // attached as a suppressed detail for the snapshot view.
            if (environment.getProperty(ERROR_ON_INSTANTANEOUS_LOOP)) {
                Loops.report(this, environment.errors, "error", loopData)
                val strippedModel = extractLoopModel(loopData)
                environment.errors.add(strippedModel.key, Loops.MESSAGE, null, Issue.SUPPRESSED)
            }
            if (environment.getProperty(WARNING_ON_INSTANTANEOUS_LOOP)) {
                Loops.report(this, environment.warnings, "warning", loopData)
                val strippedModel = extractLoopModel(loopData)
                environment.warnings.add(strippedModel.key, Loops.MESSAGE, null, Issue.SUPPRESSED)
            }
            if (environment.getProperty(INFO_ON_INSTANTANEOUS_LOOP)) {
                Loops.report(this, environment.infos, "info", loopData)
                val strippedModel = extractLoopModel(loopData)
                environment.infos.add(strippedModel.key, Loops.MESSAGE, null, Issue.SUPPRESSED)
            }
            
            // Sequential fork does not support curing schizophrenia
            // Depth join might/will disturb special dependencies
            // TODO Add support!
            for (fork : loopData.criticalNodes.filter(Fork)) {
                if (fork.isNonParallel) {
                    val issue = Issue.at("schizophrenic-fork", "A sequential region is re-entered in the tick it is left.",
                        "Make the re-entering transition delayed, or run the regions in parallel.", fork)
                    environment.errors.add(null, issue.message, fork, issue)
                }
            }
        }
    }	
	
    protected def extractLoopModel(LoopData loopData) {
        val modelCopy = getModel.copyEObjectAndReturnCopier
        val copier = modelCopy.value
        val maxStrippedNodes = environment.getProperty(LOOP_ANALYZER_MAX_STRIPPED_NODES)
        
        val reverseMap = <Node, Node> newLinkedHashMap
        for (sourceNode : copier.keySet.filter(Node)) {
            reverseMap.put(copier.get(sourceNode) as Node, sourceNode)
        }
        
        val adjacentDepth = environment.getProperty(LOOP_ANALYZER_ADJACENT_DEPTH)
        var i = 0
        for (scg : modelCopy.key.scgs) {
            for (node : scg.nodes.immutableCopy) {
                
                if (i > maxStrippedNodes) {
                    node.remove
                } else {
                
                    val sourceNode = reverseMap.get(node)
                    if (!loopData.criticalNodes.contains(sourceNode)) {
                        val adjacentNodes = sourceNode.getAdjacentNodes(<Node> newHashSet, adjacentDepth)
                        if (!adjacentNodes.exists[ loopData.criticalNodes.contains(it) ]) {
                            node.remove   
                        }
                    } else {
                        loopData.criticalNodes += node
                        i++                    
                    }
                
                }
            }
        }
        modelCopy
    }	
    
    private def Set<Node> getAdjacentNodes(Node sourceNode, Set<Node> nodes, int depth) {
        var localDepth = depth
        var Iterable<Node> iter
        if (depth < 1) {
            switch(sourceNode) {
                Entry,
                Depth:  {
                            iter = sourceNode.allNext.map[ targetNode ];
                            localDepth = 1
                        }
                Exit,
                Surface:{ 
                            iter = sourceNode.incomingLinks.map[ eContainer ].filter(Node)
                            localDepth = 1
                        } 
            }       
        } else {
            iter = sourceNode.incomingLinks.map[ eContainer ].filter(Node) +
                   sourceNode.allNext.map[ targetNode ] + 
                   sourceNode.dependencies.map[ targetNode ]
        }
        if (localDepth > 1) {
            val nList = <Node> newLinkedList            
            for (n : iter) {
                if (nodes.add(n)) {
                    nList += n
                }
            }
            val newDepth = localDepth - 1
            nList.forEach[ getAdjacentNodes(nodes, newDepth) ]
        } else {
            nodes.addAll(iter)
        } 
        nodes
    }
    
}

