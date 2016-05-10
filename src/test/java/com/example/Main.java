package com.example;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Denis on 5/10/2016.
 */
public class Main {

    private WebDriver driver;

    private DockerClient dockerClient = null;
    private CreateContainerResponse hub;
    private List<CreateContainerResponse> nodes = new ArrayList<>();

    public static final String SELENIUM_HUB_IMAGE = "selenium/hub";
    public static final String SELENIUM_NODE_FIREFOX_IMAGE = "selenium/node-firefox";

    public static final String CERT_PATH = "C:\\Users\\Denis\\.docker\\machine\\certs";
    public static final String DOCKER_CONFIG = "C:\\Users\\Denis\\.docker\\";
    public static final String DOCKER_IP = "192.168.99.100";
    public static final String DOCKER_PORT = "2376";
    public static final String GRID_PORT = "4444";
    public static final String GRID_HOST = DOCKER_IP + ":" + GRID_PORT;

    @BeforeTest
    public void setUp() throws Exception {
        //BONUS: if uncomment next two rows framework will automatically pull (if not exists) selenium images
        // and docker manage containers
        //prepareDocker(1);
        //waitForContainersStarted();
        ////////////////////////////////
        DesiredCapabilities capabilities =  DesiredCapabilities.firefox();
        driver = new RemoteWebDriver(new URL("http://" + GRID_HOST + "/wd/hub"), capabilities);
    }

    @Test
    public void testYA() throws Exception {
        driver.get("http://google.com");
        System.out.println(driver.getTitle());
    }

    @AfterTest
    public void tearDown() throws Exception {
        driver.quit();

        if (dockerClient != null){
            System.out.println("Removing containers...");
            nodes.forEach(node -> {
                dockerClient.stopContainerCmd(node.getId()).exec();
                dockerClient.removeContainerCmd(node.getId()).exec();
            });

            dockerClient.stopContainerCmd(hub.getId()).exec();
            dockerClient.removeContainerCmd(hub.getId()).exec();
        }

    }

    public void waitForContainersStarted() throws MalformedURLException, InterruptedException {
        DesiredCapabilities capabilities =  DesiredCapabilities.firefox();
        for (int i = 0 ; i < 5 ; ++i){
            try {
                new RemoteWebDriver(new URL("http://" + GRID_HOST + "/wd/hub"), capabilities);
                return;
            } catch (UnreachableBrowserException e) {
                System.out.println("containers not started yet...");
                Thread.sleep(1000);
            }
        }
    }

    public void prepareDocker(int nodesCount) throws InterruptedException {
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://" + DOCKER_IP + ":" + DOCKER_PORT)
                .withDockerTlsVerify(true)
                .withDockerCertPath(CERT_PATH)
                .withDockerConfig(DOCKER_CONFIG)
                .withApiVersion("1.21")
                .build();

        dockerClient = DockerClientBuilder.getInstance(config)
                .build();
        System.out.println("pulling images...");
        pullImage(dockerClient, SELENIUM_HUB_IMAGE);
        pullImage(dockerClient, SELENIUM_NODE_FIREFOX_IMAGE);

        System.out.println("starting containers...");
        hub = dockerClient.createContainerCmd(SELENIUM_HUB_IMAGE)
                .withPortBindings(PortBinding.parse(GRID_PORT + ":" + GRID_PORT)).withName("selenium-hub")
                .withPublishAllPorts(true).exec();

        dockerClient.startContainerCmd(hub.getId()).exec();

        for (int i = 0 ; i < nodesCount ; ++i){
            System.out.println("start container " + i + "...");
            CreateContainerResponse node = createNodeContainer();
            dockerClient.startContainerCmd(node.getId()).exec();
            nodes.add(node);
        }
    }

    private CreateContainerResponse createNodeContainer() {
        return dockerClient.createContainerCmd(SELENIUM_NODE_FIREFOX_IMAGE)
                    .withName("sel-nodes" + nodes.size())
                    .withPublishAllPorts(true)
                    .withLinks(new Link("selenium-hub", "hub")).exec();
    }

    private String runContainer(DockerClient dockerClient, String image, String command) {
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);

        if (command != null){
            containerCmd = containerCmd.withCmd(command);
        }

        CreateContainerResponse container = containerCmd.exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        dockerClient.waitContainerCmd(container.getId())
                .exec(new WaitContainerResultCallback())
                .awaitStatusCode();

        return container.getId();
    }

    private void pullImage(DockerClient dockerClient, String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            dockerClient.pullImageCmd(image).withTag("latest").exec(new PullImageResultCallback()).awaitSuccess();
        }
    }
}














