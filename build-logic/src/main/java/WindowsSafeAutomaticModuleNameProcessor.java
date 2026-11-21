import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wagyourtail.unimined.transform.processor.JarProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Workaround for an upstream Unimined 2.0.2 Windows bug in
 * {@code AutomaticModuleNameProcessor}, which opens {@code FileSystems.newFileSystem(jar)}
 * while {@code JarFile(jar.toFile())} is still open, causing Windows to fail with
 * "The process cannot access the file because it is being used by another process".
 */
public class WindowsSafeAutomaticModuleNameProcessor implements JarProcessor {

	private static final class Spec implements JarProcessorSpec {
		private static final long serialVersionUID = 1L;
		final String moduleName;
		final String manifestDonor;

		Spec(String moduleName, String manifestDonor) {
			this.moduleName = moduleName;
			this.manifestDonor = manifestDonor;
		}
	}

	@Override
	public @NotNull String getName() {
		return "automatic-module-name";
	}

	@Override
	public boolean getPreRemap() {
		return false;
	}

	@Override
	public @Nullable JarProcessorSpec buildSpec(@NotNull SpecContext context) {
		return null;
	}

	@Override
	public @NotNull String serializeSpec(@NotNull JarProcessorSpec spec) {
		Spec s = (Spec) spec;
		return s.manifestDonor != null ? s.moduleName + " " + s.manifestDonor : s.moduleName;
	}

	@Override
	public @NotNull JarProcessorSpec deserializeSpec(@NotNull String payload) {
		String[] parts = payload.trim().split(" ", 2);
		return new Spec(parts[0], parts.length > 1 ? parts[1] : null);
	}

	@Override
	public void processJar(@NotNull Path jar, @NotNull JarProcessorSpec spec, @NotNull ProcessorContext context) {
		Spec s = (Spec) spec;
		try {
			Manifest existing;
			try (JarFile jf = new JarFile(jar.toFile())) {
				ZipEntry mfEntry = jf.getEntry("META-INF/MANIFEST.MF");
				if (mfEntry != null) {
					try (InputStream ins = jf.getInputStream(mfEntry)) {
						existing = new Manifest(ins);
					}
				} else {
					existing = new Manifest();
				}
			}
			// JarFile is now closed before opening ZipFileSystem on Windows.

			if (s.moduleName.equals(existing.getMainAttributes().getValue("Automatic-Module-Name"))) {
				return;
			}
			existing.getMainAttributes().putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
			existing.getMainAttributes().putValue("Automatic-Module-Name", s.moduleName);

			if (s.manifestDonor != null) {
				try (JarFile donor = new JarFile(s.manifestDonor)) {
					ZipEntry entry = donor.getEntry("META-INF/MANIFEST.MF");
					if (entry != null) {
						Manifest donorMf;
						try (InputStream ins = donor.getInputStream(entry)) {
							donorMf = new Manifest(ins);
						}
						for (Map.Entry<String, Attributes> e : donorMf.getEntries().entrySet()) {
							Attributes attrs = e.getValue();
							boolean wants = false;
							for (Object key : attrs.keySet()) {
								String k = key.toString();
								if ("Specification-Version".equals(k) || "Implementation-Version".equals(k)) {
									wants = true;
									break;
								}
							}
							if (wants) {
								Attributes out = new Attributes();
								for (Map.Entry<Object, Object> kv : attrs.entrySet()) {
									if (!kv.getKey().toString().contains("Digest")) {
										out.put(kv.getKey(), kv.getValue());
									}
								}
								existing.getEntries().put(e.getKey(), out);
							}
						}
					}
				}
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			existing.write(baos);
			byte[] mfBytes = baos.toByteArray();

			try (FileSystem fs = FileSystems.newFileSystem(jar, Collections.emptyMap())) {
				Path p = fs.getPath("META-INF/MANIFEST.MF");
				Files.deleteIfExists(p);
				if (p.getParent() != null) {
					Files.createDirectories(p.getParent());
				}
				Files.write(p, mfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				Files.setLastModifiedTime(p, FileTime.fromMillis(0));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public @Nullable MappingsProcessor processMappings() {
		return null;
	}
}
