package jasima_gui.launcher;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS;
import jasima_gui.Activator;
import jasima_gui.util.EnablingSelectionListener;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchTab;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class LauncherParamTab extends JavaLaunchTab {

	protected Combo launcherClass;
	protected Text experimentFile;
	protected Combo logLevel;

	protected Button printResults;
	protected Button saveXML;
	protected Combo saveXMLPath;
	protected Button saveXLS;
	protected Combo saveXLSPath;

	protected Text additionalArgs;
	protected Text vmArgs;

	protected final ChangeListener updater = new ChangeListener();
	protected final Image jasimaIcon = Activator.getImageDescriptor(
			"icons/jasima.png").createImage();

	protected class ChangeListener extends SelectionAdapter implements
			ModifyListener {
		@Override
		public void modifyText(ModifyEvent e) {
			scheduleUpdateJob();
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			scheduleUpdateJob();
		}
	}

	protected static GridDataFactory indentFactory() {
		return GridDataFactory.swtDefaults().indent(20, 0);
	}

	protected static void addTitleLabel(Composite parent, String text) {
		StyledText lbl = new StyledText(parent, SWT.NONE);
		lbl.setBackground(parent.getBackground());
		lbl.setText(text);
		StyleRange range = new StyleRange();
		range.start = 0;
		range.length = text.length();
		range.fontStyle = SWT.BOLD;
		lbl.setStyleRange(range);
		GridDataFactory.swtDefaults().span(2, 1).applyTo(lbl);
	}

	protected static void addIndentedLabel(Composite parent, String text) {
		Label lbl = new Label(parent, SWT.NONE);
		lbl.setText(text);
		indentFactory().align(SWT.LEAD, SWT.CENTER).applyTo(lbl);
	}

	@Override
	public void createControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NO_RADIO_GROUP);
		setControl(comp);
		comp.setLayout(new GridLayout(2, false));

		addTitleLabel(comp, "Input");

		addIndentedLabel(comp, "Launcher class:");
		launcherClass = new Combo(comp, SWT.BORDER);
		launcherClass.add("jasima.core.util.run.ExcelExperimentRunner");
		launcherClass.add("jasima.core.util.run.XmlExperimentRunner");
		GridDataFactory.fillDefaults().grab(true, false).applyTo(launcherClass);
		launcherClass.addModifyListener(updater);

		addIndentedLabel(comp, "Experiment file:");
		experimentFile = new Text(comp, SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(experimentFile);
		experimentFile.addModifyListener(updater);

		addTitleLabel(comp, "Logging");

		addIndentedLabel(comp, "Log level:");
		logLevel = new Combo(comp, SWT.BORDER);
		logLevel.setItems(new String[] { "OFF", "ERROR", "WARN", "INFO",
				"TRACE", "ALL" });
		GridDataFactory.fillDefaults().applyTo(logLevel);
		logLevel.addModifyListener(updater);

		addTitleLabel(comp, "Results");

		printResults = new Button(comp, SWT.CHECK);
		printResults.setText("Print results to console");
		printResults.addSelectionListener(updater);
		indentFactory().span(2, 1).applyTo(printResults);

		saveXML = new Button(comp, SWT.CHECK);
		saveXML.setText("Save results to XML file:");
		saveXML.addSelectionListener(updater);
		indentFactory().applyTo(saveXML);
		saveXMLPath = new Combo(comp, SWT.BORDER);
		saveXMLPath.add("(default file name)");
		GridDataFactory.fillDefaults().applyTo(saveXMLPath);
		saveXMLPath.addModifyListener(updater);
		saveXML.addSelectionListener(new EnablingSelectionListener(saveXMLPath));

		saveXLS = new Button(comp, SWT.CHECK);
		saveXLS.setText("Save results to XLS file:");
		saveXLS.addSelectionListener(updater);
		indentFactory().applyTo(saveXLS);
		saveXLSPath = new Combo(comp, SWT.BORDER);
		saveXLSPath.add("(default file name)");
		GridDataFactory.fillDefaults().applyTo(saveXLSPath);
		saveXLSPath.addModifyListener(updater);
		saveXLS.addSelectionListener(new EnablingSelectionListener(saveXLSPath));

		addTitleLabel(comp, "Other arguments");

		addIndentedLabel(comp, "Additional launcher arguments:");
		additionalArgs = new Text(comp, SWT.BORDER);
		GridDataFactory.fillDefaults().applyTo(additionalArgs);
		additionalArgs.addModifyListener(updater);

		addIndentedLabel(comp, "Java VM arguments:");
		vmArgs = new Text(comp, SWT.BORDER);
		GridDataFactory.fillDefaults().applyTo(vmArgs);
		vmArgs.addModifyListener(updater);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(ATTR_PROGRAM_ARGUMENTS, "");
		configuration.setAttribute(ATTR_VM_ARGUMENTS, "");
	}

	public static final String addQuotes(String str) {
		return str.indexOf(' ') == -1 ? str : ("\"" + str + "\"");
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration
				.setAttribute(ATTR_MAIN_TYPE_NAME, launcherClass.getText());

		StringBuilder args = new StringBuilder();
		args.append(addQuotes("--log=" + logLevel.getText()));
		if (printResults.getSelection())
			args.append(" --printres");
		if (saveXML.getSelection()) {
			String path = saveXMLPath.getText();
			if (path.equals(saveXMLPath.getItem(0))) { // default
				args.append(" --xmlres");
			} else {
				args.append(' ');
				args.append(addQuotes("--xmlres=" + path));
			}
		}
		if (saveXLS.getSelection()) {
			String path = saveXLSPath.getText();
			if (path.equals(saveXLSPath.getItem(0))) { // default
				args.append(" --xlsres");
			} else {
				args.append(' ');
				args.append(addQuotes("--xlsres=" + path));
			}
		}

		args.append(' ');
		args.append(addQuotes(experimentFile.getText()));
		String extraArgs = additionalArgs.getText().trim();
		if (!extraArgs.isEmpty()) {
			args.append(' ');
			args.append(extraArgs);
		}

		configuration.setAttribute(ATTR_PROGRAM_ARGUMENTS, args.toString());
		configuration.setAttribute(ATTR_VM_ARGUMENTS, vmArgs.getText());
	}

	@Override
	public void initializeFrom(ILaunchConfiguration config) {
		super.initializeFrom(config);
		try {
			launcherClass.setText(config.getAttribute(ATTR_MAIN_TYPE_NAME, ""));
			vmArgs.setText(config.getAttribute(ATTR_VM_ARGUMENTS, ""));
			String args = config.getAttribute(ATTR_PROGRAM_ARGUMENTS, "");
			ArrayList<String> argsArr = new ArrayList<String>();
			while (!(args = args.trim()).isEmpty()) {
				int delim;
				if (args.startsWith("\"")) {
					args = args.substring(1);
					delim = args.indexOf('"');
					if (delim < 0) {
						argsArr.add(args);
						break;
					}
				} else {
					delim = args.indexOf(' ');
					if (delim < 0) {
						argsArr.add(args);
						break;
					}
				}
				argsArr.add(args.substring(0, delim));
				args = args.substring(delim + 1);
			}

			experimentFile.setText("");
			logLevel.setText("INFO");
			printResults.setSelection(false);
			saveXML.setSelection(false);
			saveXMLPath.select(0);
			saveXLS.setSelection(false);
			saveXLSPath.select(0);

			boolean hasFilename = false;
			boolean firstUnparsed = true;
			StringBuffer unparsed = new StringBuffer();
			for (String arg : argsArr) {
				String[] s = arg.split("=", 2);
				arg = s[0];

				if (arg.equals("--log")) {
					if (s.length > 1)
						logLevel.setText(s[1]);
				} else if (arg.equals("--printres")) {
					printResults.setSelection(true);
				} else if (arg.equals("--xmlres")) {
					saveXML.setSelection(true);
					if (s.length > 1)
						saveXMLPath.setText(s[1]);
					else
						saveXMLPath.select(0);
				} else if (arg.equals("--xlsres")) {
					saveXLS.setSelection(true);
					if (s.length > 1)
						saveXLSPath.setText(s[1]);
					else
						saveXLSPath.select(0);
				} else if (!hasFilename) {
					experimentFile.setText(arg);
					hasFilename = true;
				} else {
					if (firstUnparsed) {
						firstUnparsed = false;
					} else {
						unparsed.append(' ');
					}
					unparsed.append(addQuotes(arg));
				}
			}
			additionalArgs.setText(unparsed.toString());
		} catch (CoreException e) {
			throw new RuntimeException(e);
		} finally {
			saveXMLPath.setEnabled(saveXML.getSelection());
			saveXLSPath.setEnabled(saveXLS.getSelection());
		}
	}

	@Override
	public String getName() {
		return "Jasima";
	}

	@Override
	public Image getImage() {
		return jasimaIcon;
	}
}