/*******************************************************************************
 * Copyright (c) 2010, 2014 Torsten Hildebrandt and jasima contributors
 *
 * This file is part of jasima, v1.1.
 *
 * jasima is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jasima is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jasima.  If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id$
 *******************************************************************************/
package jasima_gui.editor;

import jasima_gui.FormTextBuilder;
import jasima_gui.JavaLinkHandler;
import jasima_gui.ProjectCache;
import jasima_gui.util.XMLUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.events.IHyperlinkListener;
import org.eclipse.ui.forms.widgets.ColumnLayout;
import org.eclipse.ui.forms.widgets.ColumnLayoutData;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;

import com.thoughtworks.xstream.XStream;

@SuppressWarnings("restriction")
public class TopLevelEditor extends EditorPart implements SelectionListener {

	public static final String CLASS_URL_PREFIX = "jasima-javaclass:";
	public static final String HREF_MORE = "jasima-command:more";
	public static final String HREF_LESS = "jasima-command:less";
	private EditorUpdater updater;
	private Object root;
	private FormToolkit toolkit = null;
	private ScrolledForm form;
	private boolean dirty = false;
	private IProject project;
	private XStream xStream;
	private ClassLoader classLoader;

	public TopLevelEditor() {
		updater = new EditorUpdater(this);
	}

	public IJavaProject getJavaProject() {
		return ProjectCache.getCache(project).getJavaProject();
	}

	public XStream getXStream() {
		return xStream;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public FormToolkit getToolkit() {
		return toolkit;
	}

	@Override
	public void dispose() {
		updater.dispose();
		if (toolkit != null) {
			toolkit.dispose();
		}
		super.dispose();
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		try {
			IFileEditorInput fei = (IFileEditorInput) input;
			setFileInput(fei);
			setSite(site);
			fei.getFile().refreshLocal(0, null);
			InputStream is = fei.getStorage().getContents();
			try {
				root = xStream.fromXML(is);
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			root = e;
		}
	}

	protected void setFileInput(IFileEditorInput input) {
		// FIXME moving into an incompatible project should be detected
		project = input.getFile().getProject();
		xStream = ProjectCache.getCache(project).getXStream();
		classLoader = xStream.getClassLoader();
		super.setInput(input);
		updateHeadline();
	}

	protected boolean isValidData() {
		return !(root instanceof Exception);
	}

	@Override
	public boolean isSaveAsAllowed() {
		return isValidData();
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	protected void updateHeadline() {
		if (form == null)
			return;

		setPartName(getEditorInput().getName());

		Link headline = new Link(form.getForm().getHead(), 0);
		headline.setForeground(form.getForeground());
		headline.setBackground(null);
		headline.setFont(form.getFont());
		if (isValidData()) {
			headline.setText(String.format("%s - <a href=\"%s%s\">%s</a>", getEditorInput().getName(),
					CLASS_URL_PREFIX, root.getClass().getCanonicalName(), root.getClass().getSimpleName()));
		} else {
			headline.setText(getEditorInput().getName());
		}
		headline.addSelectionListener(this);

		Control head = form.getForm().getHeadClient();
		if (head != null)
			head.dispose();
		form.setHeadClient(headline);
		toolkit.decorateFormHeading(form.getForm());
	}

	@Override
	public void createPartControl(Composite parent) {
		toolkit = new FormToolkit(parent.getDisplay());

		form = toolkit.createScrolledForm(parent);
		form.setExpandHorizontal(true);
		updateHeadline();

		if (!isValidData()) {
			GridLayout grid = new GridLayout(2, false);
			grid.marginTop = 10;
			form.getBody().setLayout(grid);
			String msg = String.format("Error reading input: %s: %s", root.getClass().getSimpleName(),
					((Exception) root).getLocalizedMessage().replaceFirst("^ *: *", ""));
			Label icon = toolkit.createLabel(form.getBody(), null);
			icon.setImage(form.getDisplay().getSystemImage(SWT.ERROR));
			toolkit.createLabel(form.getBody(), msg, SWT.WRAP);
			return;
		}

		Layout layout = new Layout() {
			static final int SPACING = 10;
			static final int VMARGIN = 10;
			static final int HMARGIN = 5;

			@Override
			protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
				Point retVal = new Point(composite.getSize().x, 0);
				if(retVal.x == 0) {
					retVal.x = -1;
				} else {
					retVal.x -= 2 * HMARGIN;
				}
				retVal.y += VMARGIN;
				for (Control c : composite.getChildren()) {
					retVal.y += SPACING;
					retVal.y += c.computeSize(retVal.x, SWT.DEFAULT).y;
				}
				retVal.y += VMARGIN - SPACING;
				retVal.x = 0;
				return retVal;
			}

			@Override
			protected void layout(Composite composite, boolean flushCache) {
				int w = composite.getSize().x - 2 * HMARGIN;
				int posY = VMARGIN;
				for (Control c : composite.getChildren()) {
					Point size = c.computeSize(w, SWT.DEFAULT);
					c.setSize(size);
					c.setLocation(HMARGIN, posY);
					posY += size.y;
					posY += SPACING;
				}
			}
		};
		form.getBody().setLayout(layout);

		try {
			IType type = getJavaProject().findType(root.getClass().getCanonicalName());
			String doc = JavadocContentAccess2.getHTMLContent(type, true);

			FormTextBuilder bldr;

			bldr = new FormTextBuilder();
			bldr.parseBadHtml(doc);
			bldr.startParagraph();
			bldr.startLink(HREF_LESS);
			bldr.addText("Hide detailed description");
			bldr.finishLink();
			final String longDescription = bldr.finish();

			bldr = new FormTextBuilder();
			bldr.parseBadHtml(doc.substring(0, doc.indexOf('.') + 1));
			bldr.addText(" ");
			bldr.startLink(HREF_MORE);
			bldr.addText("more");
			bldr.finishLink();
			final String summary = bldr.finish();

			final FormText documentation = toolkit.createFormText(form.getBody(), true);
			documentation.addHyperlinkListener(new IHyperlinkListener() {

				@Override
				public void linkExited(HyperlinkEvent evt) {
				}

				@Override
				public void linkEntered(HyperlinkEvent evt) {
				}

				@Override
				public void linkActivated(HyperlinkEvent evt) {
					String href = evt.getHref().toString();
					if (href.equals(HREF_MORE)) {
						documentation.setText(longDescription, true, false);
						form.getBody().layout(true, true);
						form.reflow(true);
					} else if (href.equals(HREF_LESS)) {
						documentation.setText(summary, true, false);
						form.getBody().layout(true, true);
						form.reflow(true);
					} else {
						JavaLinkHandler handler = new JavaLinkHandler();
						try {
							IJavaElement el = JavaElementLinks.parseURI(new URI(href));
							if (el == null) {
								handler.handleExternalLink(new URL(href), evt.display);
							} else {
								handler.handleJavadocViewLink(el);
							}
						} catch (MalformedURLException e) {
						} catch (URISyntaxException e) {
						}
					}
				}
			});
			documentation.setLayoutData(new ColumnLayoutData(0));
			documentation.setText(summary, true, false);
		} catch (CoreException e) {
		}

		IProperty topLevelProperty = new IProperty() {
			public void setValue(Object val) throws PropertyException {
				root = val;
				makeDirty();
			}

			public boolean isWritable() {
				return true;
			}

			public boolean isImportant() {
				return true;
			}

			public Object getValue() throws PropertyException {
				return root;
			}

			public Class<?> getType() {
				return root.getClass();
			}

			public String getName() {
				return getEditorInput().getName();
			}

			@Override
			public String getHTMLDescription() {
				return "";
			}

			@Override
			public boolean canBeNull() {
				return false;
			}
		};

		EditorWidget editor = EditorWidgetFactory.getInstance().createEditorWidget(this, form.getBody(),
				topLevelProperty, null);
		editor.loadValue();
	}

	@Override
	public void setFocus() {
		form.setFocus();
	}

	protected void doSaveReally() throws CoreException {
		assert isValidData();
		byte[] byteArr = XMLUtil.serialize(ProjectCache.getCache(project).getXStream(), root);
		IFileEditorInput fei = (IFileEditorInput) getEditorInput();
		if (fei.getFile().exists()) {
			fei.getFile().setContents(new ByteArrayInputStream(byteArr), false, true, null);
		} else {
			fei.getFile().create(new ByteArrayInputStream(byteArr), false, null);
		}
		dirty = false;
		firePropertyChange(PROP_DIRTY);
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		try {
			doSaveReally();
		} catch (CoreException e) {
			ErrorDialog.openError(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
					"Couldn't save file", null, e.getStatus());
		}
	}

	@Override
	public void doSaveAs() {
		// TODO compare to AbstractDecoratedTextEditor.performSaveAs
		SaveAsDialog dlg = new SaveAsDialog(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell());
		IFileEditorInput oldInput = (IFileEditorInput) getEditorInput();
		dlg.setOriginalFile(oldInput.getFile());
		dlg.create();
		if (dlg.open() == SaveAsDialog.CANCEL)
			return;
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(dlg.getResult());
		FileEditorInput input = new FileEditorInput(file);
		setInput(input);
		try {
			doSaveReally();
			firePropertyChange(PROP_INPUT);
			updateHeadline();
		} catch (CoreException e) {
			setInput(oldInput);
			ErrorDialog.openError(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
					"Couldn't save file", null, e.getStatus());
		}
	}

	public void makeDirty() {
		if (dirty)
			return;
		dirty = true;
		firePropertyChange(PROP_DIRTY);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// ignore
	}

	@Override
	public void widgetSelected(SelectionEvent evt) {
		try {
			String href = evt.text;
			if (href.startsWith(CLASS_URL_PREFIX)) {
				IJavaElement elem = getJavaProject().findType(href.substring(CLASS_URL_PREFIX.length()));
				IEditorPart part = JavaUI.openInEditor(elem);
				JavaUI.revealInEditor(part, elem);
			}
		} catch (Exception e) {
			// ignore
		}
	}
}
