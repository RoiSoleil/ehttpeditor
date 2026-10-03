package org.eclipse.ehttpeditor.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** The files written for the user, with commented examples: the files of the folder templates. */
final class Templates {

	/** The HTTP file of the first start and of File > New HTTP File... */
	static final String REQUESTS = "requests.http"; //$NON-NLS-1$

	private Templates() {
	}

	/** Writes the template of this name (requests.http, http-client.env.json...) in the file. */
	static void write(String name, File file) throws IOException {
		file.getAbsoluteFile().getParentFile().mkdirs();
		try (InputStream template = Templates.class.getResourceAsStream("/templates/" + name)) { //$NON-NLS-1$
			Files.copy(template, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
