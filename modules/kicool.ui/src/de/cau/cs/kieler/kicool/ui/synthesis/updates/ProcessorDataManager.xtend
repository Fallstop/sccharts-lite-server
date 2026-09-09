/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2017 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.kicool.ui.synthesis.updates

import de.cau.cs.kieler.kicool.ProcessorGroup
import de.cau.cs.kieler.kicool.ProcessorReference
import de.cau.cs.kieler.kicool.compilation.ProcessorStatus
import de.cau.cs.kieler.kicool.compilation.RuntimeSystems
import de.cau.cs.kieler.kicool.compilation.observer.AbstractCompilationNotification
import de.cau.cs.kieler.kicool.compilation.observer.AbstractContextNotification
import de.cau.cs.kieler.kicool.compilation.observer.AbstractProcessorNotification
import de.cau.cs.kieler.kicool.compilation.observer.CompilationChanged
import de.cau.cs.kieler.kicool.compilation.observer.CompilationStart
import de.cau.cs.kieler.kicool.compilation.observer.ProcessorFinished
import de.cau.cs.kieler.kicool.environments.EnvironmentPair
import de.cau.cs.kieler.kicool.environments.Errors
import de.cau.cs.kieler.kicool.environments.MessageObjectReferences
import de.cau.cs.kieler.kicool.environments.Snapshots
import de.cau.cs.kieler.kicool.environments.Warnings
import de.cau.cs.kieler.kicool.ui.synthesis.MessageObjectListPair
import de.cau.cs.kieler.kicool.ui.synthesis.ProcessorSynthesis
import de.cau.cs.kieler.kicool.ui.synthesis.actions.IntermediateData
import de.cau.cs.kieler.kicool.ui.synthesis.actions.OnOffToggle
import de.cau.cs.kieler.kicool.ui.synthesis.actions.SelectAdditionalIntermediateAction
import de.cau.cs.kieler.kicool.ui.synthesis.actions.ToggleProcessorOnOffAction
import de.cau.cs.kieler.kicool.ui.synthesis.feedback.PostUpdateDoubleCollector
import de.cau.cs.kieler.kicool.ui.synthesis.styles.ColorSystem
import de.cau.cs.kieler.kicool.ui.synthesis.styles.ProcessorStyles
import de.cau.cs.kieler.klighd.LightDiagramLayoutConfig
import de.cau.cs.kieler.klighd.LightDiagramServices
import de.cau.cs.kieler.klighd.kgraph.KEdge
import de.cau.cs.kieler.klighd.kgraph.KNode
import de.cau.cs.kieler.klighd.kgraph.KShapeLayout
import de.cau.cs.kieler.klighd.krendering.KBackground
import de.cau.cs.kieler.klighd.krendering.KColor
import de.cau.cs.kieler.klighd.krendering.KColoring
import de.cau.cs.kieler.klighd.krendering.KContainerRendering
import de.cau.cs.kieler.klighd.krendering.KForeground
import de.cau.cs.kieler.klighd.krendering.KPolygon
import de.cau.cs.kieler.klighd.krendering.KPolyline
import de.cau.cs.kieler.klighd.krendering.KRendering
import de.cau.cs.kieler.klighd.krendering.KRenderingFactory
import de.cau.cs.kieler.klighd.krendering.KStyle
import de.cau.cs.kieler.klighd.krendering.KText
import de.cau.cs.kieler.klighd.krendering.LineCap
import de.cau.cs.kieler.klighd.krendering.LineJoin
import de.cau.cs.kieler.klighd.krendering.Trigger
import de.cau.cs.kieler.klighd.krendering.extensions.KEdgeExtensions
import de.cau.cs.kieler.klighd.krendering.extensions.KRenderingExtensions
import de.cau.cs.kieler.klighd.krendering.extensions.PositionReferenceX
import de.cau.cs.kieler.klighd.krendering.extensions.PositionReferenceY
import java.util.List
import org.eclipse.elk.core.options.CoreOptions

import static de.cau.cs.kieler.kicool.compilation.Metric.*
import static de.cau.cs.kieler.kicool.environments.Environment.*
import static de.cau.cs.kieler.kicool.ui.synthesis.KNodeProperties.*
import static de.cau.cs.kieler.kicool.ui.synthesis.styles.ColorStore.Color.*
import static de.cau.cs.kieler.kicool.ui.synthesis.styles.ColorSystem.*

import static extension de.cau.cs.kieler.kicool.ui.synthesis.styles.ColorStore.*
import static extension de.cau.cs.kieler.kicool.ui.synthesis.updates.MessageObjectReferencesManager.fillUndefinedColors
import static extension de.cau.cs.kieler.kicool.util.KiCoolUtils.uniqueProcessorId
import static extension org.eclipse.emf.ecore.util.EcoreUtil.*
import static extension org.eclipse.xtext.EcoreUtil2.*

/**
 * The data manager handles all synthesis updates.
 * 
 * @author ssm
 * @kieler.design 2017-02-27 proposed 
 * @kieler.rating 2017-02-27 proposed yellow
 */
class ProcessorDataManager {
    
    static val KRenderingFactory renderingFactory = KRenderingFactory::eINSTANCE
    static KRenderingExtensions kRenderingExtensions = new KRenderingExtensions
    static KEdgeExtensions kEdgeExtensions = new KEdgeExtensions
    static ProcessorSynthesis processorSynthesis = new ProcessorSynthesis()
    static ProcessorStyles processorStyles = new ProcessorStyles()
    
    
    static def void populateProcessorData(ProcessorReference processorReference, KNode node) {
        node.setProperty(PROCESSOR_IDENTIFIER, processorReference)
        
        val rtProcessor = RuntimeSystems.getProcessorInstance(processorReference)
        
        if (rtProcessor === null) {
            node.setFrameErrorColor
            node.getAllContentsOfType(KText).head.text = processorReference.id.split("\\.").last
            return;
        }
        val nameStr = rtProcessor.name
        node.getAllContentsOfType(KText).head.text = nameStr
        
        val toggleOnOffButton = node.getProperty(PROCESSOR_ON_OFF_BUTTON)
        if (toggleOnOffButton !== null) {
            kRenderingExtensions.addAction(toggleOnOffButton, Trigger::SINGLECLICK, ToggleProcessorOnOffAction.ID)
            val toggle = ToggleProcessorOnOffAction.deactivatedProcessors.get(processorReference)
            if (toggle === null || toggle == OnOffToggle.ON) {
                setFBColor(toggleOnOffButton.children.head, ON)
            } else if (toggle == OnOffToggle.OFF) {
                setFBColor(toggleOnOffButton.children.head, OFF)
            } else {
                setFBColor(toggleOnOffButton.children.head, HALT)
            }
        }
    }
    
    static def void setCompatibilityError(KNode node) {
        node.setFrameErrorColor
    }
    
    
    static def void setFrameErrorColor(KNode node) {
        val rect = node.getData(KContainerRendering) as KContainerRendering
        rect.setBColor(ERROR)
    }
    
    static def void setBColor(KRendering container, ColorSystem colorSystem) {
        container.setBColors(colorSystem.background.color, colorSystem.backgroundTarget.color)
    }
    
    static def void setFBColor(KRendering container, ColorSystem colorSystem) {
        container.setFBColors(colorSystem.foreground.color, colorSystem.background.color, colorSystem.backgroundTarget.color)
    }
    
    static def void setFBColorViaExtension(KRendering container, ColorSystem colorSystem) {
        container.setFBColorsViaExtension(colorSystem.foreground.color, colorSystem.background.color, colorSystem.backgroundTarget.color)
    }

    static def void setFBAColor(KRendering container, ColorSystem colorSystem, int alpha) {
        container.setFBAColors(colorSystem.foreground.color, colorSystem.background.color, colorSystem.backgroundTarget.color, alpha)
    }
    
    static def void setBAlpha(KRendering container, int alpha) {
        container.styles.filter(KColoring).forEach[ c |
            if (c instanceof KBackground) { 
                c.alpha = alpha
                c.targetAlpha = alpha
            }
        ]
    }
    

    
    /**
     * Private because KColors are not copied.
     */
    private static def void setFBColors(KRendering container, KColor foreground, KColor background, KColor backgroundTarget) {
        container.styles.filter(KColoring).forEach[ c |
            if (c instanceof KForeground) c.color = foreground
            if (c instanceof KBackground) { 
                c.color = background
                c.targetColor = backgroundTarget
            }
        ]
    }
    
    private static def void setBColors(KRendering container, KColor background, KColor backgroundTarget) {
        container.styles.filter(KColoring).forEach[ c |
            if (c instanceof KBackground) { 
                c.color = background
                c.targetColor = backgroundTarget
            }
        ]
    }
    
    private static def void setFBColorsViaExtension(KRendering container, KColor foreground, KColor background, KColor backgroundTarget) {
        kRenderingExtensions.setForeground(container, foreground)
        kRenderingExtensions.setBackgroundGradient(container, background, backgroundTarget, 0)
    }
    
    private static def void setFBAColors(KRendering container, KColor foreground, KColor background, KColor backgroundTarget, int alpha) {
        container.styles.filter(KColoring).forEach[ c |
            if (c instanceof KForeground) c.color = foreground
            if (c instanceof KBackground) { 
                c.color = background
                c.targetColor = backgroundTarget
                c.alpha = alpha
                c.targetAlpha = alpha
            }
        ]
    }
    
    static def getContainer(KNode node) {
        node.getData(KContainerRendering) as KContainerRendering
    }
    
    static def getContainers(KNode node) {
        node.data.filter(KContainerRendering) 
    }

    static def getContainer(KEdge edge) {
        edge.getData(KContainerRendering) as KContainerRendering
    }
    
    static def KNode findNode(KNode node, String id) {
        node.eAllContents.filter(KNode).filter[ getProperty(PROCESSOR_IDENTIFIER)?.uniqueProcessorId == id ]?.head
    }
        
    static def findAllNodes(KNode node, String id) {
        node.eAllContents.filter(KNode).filter[ getProperty(PROCESSOR_IDENTIFIER)?.uniqueProcessorId == id ].toList
    }
    
    static def KShapeLayout getShapeLayout(KNode node) {
        node.eContents.filter(KShapeLayout).head
    }
    
    static private def <T extends KRendering> void removeAllActions(T rendering) {
        rendering.actions.clear
    }    
    
    
    static private def KRendering internalAddArrowDecorator(KPolyline pl, boolean head) {
        kRenderingExtensions.setLineCap(pl, LineCap::CAP_FLAT)
        return pl.drawArrow => [
            it.placementData = renderingFactory.createKDecoratorPlacementData => [
                it.rotateWithLine = true;
                it.relative = if (head) 1f else 0f;
                it.absolute = if (head) -2f else 2f;
                it.width = 6;
                it.height = 4;
                it.setXOffset(if (head) -4f else 6f); // chsch: used the regular way here and below, as the alias 
                it.setYOffset(if (head) -2f else 3f); //  name translation convention changed from Xtext 2.3 to 2.4.
            ];
            if (!head) kRenderingExtensions.setRotation(it, 180f)
        ];
    }   
    
    static private def <T extends KRendering> T addChild(KContainerRendering parent, T child) {
        return child => [
            parent.children.add(it);
        ];
    }      
    
    static private def KPolygon drawArrow(KContainerRendering cr) {
        return renderingFactory.createKPolygon => [
            kRenderingExtensions.setLineJoin(
                kRenderingExtensions.setBackground(cr.addChild(it).withCopyOf(kRenderingExtensions.getLineWidth(cr)).withCopyOf(kRenderingExtensions.getForeground(cr)), 
                    kRenderingExtensions.getForeground(cr)
                ),
                LineJoin.JOIN_ROUND
            )
            it.points += kRenderingExtensions.createKPosition(PositionReferenceX::LEFT, 0, 0, PositionReferenceY::TOP, 0, 0);
            it.points += kRenderingExtensions.createKPosition(PositionReferenceX::LEFT, 0, 0.66f, PositionReferenceY::TOP, 0, 0.5f);
            it.points += kRenderingExtensions.createKPosition(PositionReferenceX::LEFT, 0, 0, PositionReferenceY::BOTTOM, 0, 0);
            it.points += kRenderingExtensions.createKPosition(PositionReferenceX::RIGHT, 0, 0, PositionReferenceY::BOTTOM, 0, 0.5f);    
       ]
    }
    
    static private def <T extends KRendering> T withCopyOf(T rendering, KStyle style) {
        rendering.styles += style.copy;
        return rendering;
    }

}
