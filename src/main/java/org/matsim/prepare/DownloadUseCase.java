package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.ScenariosTools;
import picocli.CommandLine;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.matsim.utils.Tools.downloadFile;

/**
 * Download the read-to-use scenarios to a local folder. Then simulations can be run locally
 */
@Deprecated
public class DownloadUseCase implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(DownloadUseCase.class);

    @CommandLine.Option(names = "--target", description = "target folder of the scenario", required = true)
    private Path target;

    @CommandLine.Option(names = "--scenario", description = "Use case to download, choose from: " +
            "BERLIN_DRT, LEIPZIG_DRT, VULKANEIFEL_SCHOOL_TRANSPORT, KELHEIM_KEXI, ORANIENBURG", defaultValue = "BERLIN_DRT")
    private ScenariosTools.UseCases useCase;

    public static void main(String[] args) {
        new DownloadUseCase().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        log.info("Downloading " + useCase + " scenario to " + target.toString());
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        String configPath;
        switch (useCase) {
            case BERLIN_DRT ->
                    configPath = "scenarios/berlin-drt/berlin-drt.config.xml";
            case LEIPZIG_DRT -> configPath = "abc";
            case VULKANEIFEL_SCHOOL_TRANSPORT -> configPath = "";
            case KELHEIM_KEXI -> configPath = "123";
            case ORANIENBURG -> configPath = "456";
            default -> throw new RuntimeException("Not implemented. Please choose from " +
                    "[BERLIN_DRT, LEIPZIG_DRT, VULKANEIFEL_SCHOOL_TRANSPORT, KELHEIM_KEXI, ORANIENBURG]");
        }

        // Download config file
        log.info("Downloading config file");
        downloadFile(configPath, target.toString() + "/" + useCase.toString().toLowerCase() + ".config.xml");
        Config config = ConfigUtils.loadConfig(configPath);
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Download network
        log.info("Downloading network file");
        String networkFileName = config.network().getInputFile();
        new NetworkWriter(scenario.getNetwork()).write(target.toString() + "/" + networkFileName);

        // Download plans
        log.info("Downloading plans file");
        String plansFileName = config.plans().getInputFile();
        new PopulationWriter(scenario.getPopulation()).write(target.toString() + "/" + plansFileName);

        // Get drt vehicles
        log.info("Downloading vehicles file");
        String targetVehiclesZipFile = target + "/drt-vehicles.zip";
        String drtVehiclesZip = getParentUrl(new URL(configPath)) + "/drt-vehicles.zip";
        downloadFile(drtVehiclesZip, targetVehiclesZipFile);
        unzip(targetVehiclesZipFile, String.valueOf(target));
        Files.delete(Path.of(targetVehiclesZipFile));

        return 0;
    }

    private static String getParentUrl(URL url) {
        String urlString = url.toString();
        int lastSlashIndex = urlString.lastIndexOf('/');

        if (lastSlashIndex != -1) {
            return urlString.substring(0, lastSlashIndex);
        } else {
            return urlString;
        }
    }

    public static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            Files.createDirectories(destDir.toPath());
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryPath = destDirectory + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    if (!entryPath.contains("__MACOSX")) {
                        extractFile(zipInputStream, entryPath);
                    }
                } else {
                    File dir = new File(entryPath);
                    Files.createDirectories(dir.toPath());
                }
                zipInputStream.closeEntry();
            }
        }
        System.out.println("ZIP file extracted successfully to: " + destDirectory);
    }

    private static void extractFile(ZipInputStream zipInputStream, String entryPath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(entryPath))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipInputStream.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }


}
