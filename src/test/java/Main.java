
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
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
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.List;

/**
 * Created by Denis on 5/10/2016.
 */
public class Main {

    private WebDriver driver;

    private DockerClient dockerClient;
    private CreateContainerResponse hub;
    private CreateContainerResponse node;

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
        prepareDocker();
        DesiredCapabilities capabilities =  DesiredCapabilities.firefox();
        //MY SELENIUM GRID DOCKER ADDRESS
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
        dockerClient.stopContainerCmd(node.getId()).exec();
        dockerClient.stopContainerCmd(hub.getId()).exec();
        dockerClient.removeContainerCmd(node.getId()).exec();
        dockerClient.removeContainerCmd(hub.getId()).exec();
    }

    public void prepareDocker(){
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://" + DOCKER_IP + ":" + DOCKER_PORT)
                .withDockerTlsVerify(true)
                .withDockerCertPath(CERT_PATH)
                .withDockerConfig(DOCKER_CONFIG)
                .withApiVersion("1.21")
                .build();

        DockerCmdExecFactoryImpl dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(1000)
                .withConnectTimeout(1000)
                .withMaxTotalConnections(100)
                .withMaxPerRouteConnections(10);


        dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();

        pullImage(dockerClient, SELENIUM_HUB_IMAGE);
        pullImage(dockerClient, SELENIUM_NODE_FIREFOX_IMAGE);

        hub = dockerClient.createContainerCmd(SELENIUM_HUB_IMAGE)
                .withPortBindings(PortBinding.parse(GRID_PORT + ":" + GRID_PORT)).withName("selenium-hub")
                .withPublishAllPorts(true).exec();

        node = dockerClient.createContainerCmd(SELENIUM_NODE_FIREFOX_IMAGE).withName("sel-node")
                .withPublishAllPorts(true)
                .withLinks(new Link("selenium-hub", "hub")).exec();

        dockerClient.startContainerCmd(hub.getId()).exec();
        dockerClient.startContainerCmd(node.getId()).exec();
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














