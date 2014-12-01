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
package jasima_gui.launcher;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;
import jasima_gui.pref.Pref;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;

public class SimulationLaunchShortcut implements ILaunchShortcut2 {

	public void launch(IResource resource, String mode) {
		if (resource == null)
			return;
		try {
			String pathArg = resource.getProjectRelativePath().toString();
			if (pathArg.contains("\"") || pathArg.contains("\\")) {
				Status status = new Status(Status.ERROR, "jasima_gui",
						"The file name must not contain backslashes or quotation marks.");
				ErrorDialog.openError(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(), "Error",
						"Can't create launch configuration.", status);
				return;
			}

			ILaunchConfiguration cfg = findConfiguration(resource);

			if (cfg == null) {
				String baseName = resource.getProjectRelativePath().removeFileExtension().toString();
				ILaunchConfigurationWorkingCopy wc = getConfigurationType().newInstance(null,
						getLaunchManager().generateLaunchConfigurationName(baseName));
				wc.setAttribute(ATTR_MAIN_TYPE_NAME, getLauncherClass());
				wc.setAttribute(ATTR_PROJECT_NAME, resource.getProject().getName());
				wc.setAttribute(ATTR_PROGRAM_ARGUMENTS, getDefaultArgs() + " \"" + pathArg + "\"");
				wc.setMappedResources(new IResource[] { resource });
				cfg = wc.doSave();
			}

			DebugUITools.launch(cfg, mode);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static ILaunchConfiguration findConfiguration(IResource resource) {
		try {
			ILaunchConfigurationType configType = getConfigurationType();
			ILaunchConfiguration[] configs = getLaunchManager().getLaunchConfigurations(getConfigurationType());
			for (ILaunchConfiguration config : configs) {
				if (config.getType() != configType)
					continue;

				IResource[] res = config.getMappedResources();
				if (res.length != 1)
					continue; // old configuration, ignore it

				if (res[0].equals(resource)) {
					return config;
				}
			}
			return null;
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}

	protected String getDefaultArgs() {
		return Pref.EXP_RES_FMT.val();
	}

	protected static ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	protected static ILaunchConfigurationType getConfigurationType() {
		return getLaunchManager().getLaunchConfigurationType("jasima_gui.jasimaLaunchConfigurationType");
	}

	protected String getLauncherClass() {
		return "jasima.core.util.run.XmlExperimentRunner";
	}

	@Override
	public IResource getLaunchableResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			if (ss.size() == 1) {
				Object element = ss.getFirstElement();
				if (element instanceof IAdaptable) {
					return (IResource) ((IAdaptable) element).getAdapter(IResource.class);
				}
			}
		}
		return null;
	}

	@Override
	public IResource getLaunchableResource(IEditorPart editorpart) {
		IEditorInput inp = editorpart.getEditorInput();
		if (!(inp instanceof IFileEditorInput)) {
			return null;
		}
		return ((IFileEditorInput) inp).getFile();
	}

	@Override
	public void launch(ISelection selection, String mode) {
		launch(getLaunchableResource(selection), mode);
	}

	@Override
	public void launch(IEditorPart editorpart, String mode) {
		launch(getLaunchableResource(editorpart), mode);
	}

	@Override
	public ILaunchConfiguration[] getLaunchConfigurations(ISelection selection) {
		// let the framework resolve configurations based on resource mapping
		return null;
	}

	@Override
	public ILaunchConfiguration[] getLaunchConfigurations(IEditorPart editorpart) {
		// let the framework resolve configurations based on resource mapping
		return null;
	}
}
