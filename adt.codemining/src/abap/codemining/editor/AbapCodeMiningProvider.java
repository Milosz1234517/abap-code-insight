/**
 *  Copyright (c) 2017 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     Angelo Zerr <angelo.zerr@gmail.com> - [CodeMining] Provide Java References/Implementation CodeMinings - Bug 529127
 */
package abap.codemining.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.texteditor.ITextEditor;

import com.sap.adt.tools.core.IAdtObjectReference;

import abap.codemining.element.AbapClassElementParser;
import abap.codemining.element.CdsElementParser;
import abap.codemining.element.FunctionModuleElementParser;
import abap.codemining.element.IAbapElementParser;
import abap.codemining.element.NotSupportedElementParser;
import abap.codemining.element.parser.ReportElementParser;
import abap.codemining.element.parser.StructureElementParser;
import abap.codemining.feature.FeatureFacade;
import abap.codemining.general.AbapEditorCodeMining;
import abap.codemining.plugin.AbapCodeInsightPluginHelper;

public class AbapCodeMiningProvider extends AbstractCodeMiningProvider {

	AbapCodeInsightPluginHelper abapCodeMiningPluginHelper;
	private FeatureFacade featureFacade;
	private EditorFacade textEditorFacade;
	private final TextEditorMiningsProxy textEditorMiningsProxy;

	public AbapCodeMiningProvider() {
		abapCodeMiningPluginHelper = new AbapCodeInsightPluginHelper();
		featureFacade = new FeatureFacade();
		textEditorMiningsProxy = abapCodeMiningPluginHelper.getTextEditorMiningsProxy();

		registerPreferencePropertyChangeListener();
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		return CompletableFuture.supplyAsync(() -> {
			//monitor.isCanceled();
			final ITextEditor textEditor = super.getAdapter(ITextEditor.class);

			if (featureFacade.getPerformanceFeature().isActive()) {
				Display.getDefault().asyncExec(new Runnable() {
				    public void run() {
				    	abapCodeMiningPluginHelper.reinitViewInReconcilers(viewer);
				    }
				});
			}

			try {

				final List<ICodeMining> minings = new ArrayList<>();
				collectMinings(textEditor, viewer, minings, featureFacade.getPerformanceFeature().getMaxLines());
				return minings;

			} catch (final JavaModelException e) {
				// TODO: what should we done when there are some errors?
			}

			return Collections.emptyList();
		});
	}

	private void registerPreferencePropertyChangeListener() {

		abapCodeMiningPluginHelper.getPreferenceStore().addPropertyChangeListener(event -> {
			featureFacade = new FeatureFacade();
		});

	}

	private void collectMinings(ITextEditor textEditor, ITextViewer viewer, List<ICodeMining> minings, int maxLines)
			throws JavaModelException {

		textEditorFacade = new EditorFacade(textEditor);

		if (textEditorFacade.getAbapProject() != null
				&& textEditorFacade.getDocument().getNumberOfLines() <= maxLines) {

			if (textEditor.isDirty()) {
				minings.addAll(textEditorMiningsProxy
						.get(new TextEditorMiningEntryKey(textEditorFacade.getAbapProject().getProject().getName(),
								textEditor.getTitle()))
						.calculateEditedMinings(textEditorFacade.getDocument()));
			} else {
				IAbapElementParser abapElementParser;

				final IAdtObjectReference adtObjectReference = textEditorFacade.getAdtObjectReference();
				if (adtObjectReference != null) {
					switch (adtObjectReference.getType()) {
					case "CLAS/I":
					case "INTF/OI":
						abapElementParser = new AbapClassElementParser(featureFacade, adtObjectReference.getUri(),
								textEditorFacade.getAbapProject());
						break;
					case "DDLS/DF":
						abapElementParser = new CdsElementParser(featureFacade);
						break;
					case "FUGR/FF":
						abapElementParser = new FunctionModuleElementParser(featureFacade);
						break;
					case "PROG/P":
						abapElementParser = new ReportElementParser(featureFacade);
						break;
					case "TABL/DS":
						abapElementParser = new StructureElementParser(featureFacade);
						break;
					default:
						abapElementParser = new NotSupportedElementParser(featureFacade);
						break;
					}

					final AbapEditorCodeMining abapClassCodeMining = new AbapEditorCodeMining(textEditor, viewer,
							abapElementParser);
					abapClassCodeMining.evaluateCodeMinings(minings, this);

					textEditorMiningsProxy.addOrUpdate(textEditorFacade.getAbapProject(), textEditor.getTitle(),
							minings);
				}
			}
		}
	}

	private void reuseSavedCodeMinings() {
		// TODO Auto-generated method stub

	}
}