package com.whinc.controller;

import com.whinc.Config;
import com.whinc.algorithm.Eigenvector;
import com.whinc.algorithm.Kmeans;
import com.whinc.model.NetworkAdapter;
import com.whinc.model.PacketInfo;
import com.whinc.pcap.ClusterModule;
import com.whinc.pcap.PcapManager;
import com.whinc.ui.OptionDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.Chart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

/**
 * Created by Administrator on 2016/3/2.
 */
public class MainFormController {
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss S");
    @FXML
    public Label statusInfoLabel;
    public TextArea packetDetailText;
    @FXML
    public TextArea logText;
    @FXML
    public Tab logTab;
    @FXML
    public TabPane tabPane;
    @FXML
    public Menu menuKmeansAlgorithm;
    /**
     * 协议组成饼状图
     */
    @FXML
    public PieChart pieChart;
    @FXML
    public ScatterChart scatterChart;
    private Stage stage;
    @FXML
    public MenuItem menuItemStop;
    @FXML
    private TableView tableView;
    @FXML
    private MenuItem menuItemStart;
    private Chart curVisibleChart = null;   // 记录当前可见的图表

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    protected void handlerButtonClick() {
        Dialog<Object> dialog = new Dialog<>();
        dialog.setContentText("hello");
        dialog.showAndWait();
    }


    /**
     * 初始化 （该方法在FXMLLoader加载布局文件时自动调用）
     */
    @FXML
    protected void initialize() {
        System.out.println("Begin initialize");

        ObservableList<TableColumn<PacketInfo, String>> columns = tableView.getColumns();
        TableColumn<PacketInfo, String> numCol = columns.get(0);
        numCol.setCellValueFactory(param -> {
            PacketInfo packetInfo = param.getValue();
            return new SimpleStringProperty(String.valueOf(packetInfo.getNumber()));
        });
        TableColumn<PacketInfo, String> timeCol = columns.get(1);
        timeCol.setCellValueFactory(param -> {
            PacketInfo packetInfo = param.getValue();
            return new SimpleStringProperty(String.format("%.6f", packetInfo.getTimestamp() / 1e6));
        });
        TableColumn<PacketInfo, String> srcCol = columns.get(2);
        srcCol.setCellValueFactory(param -> {
            return new SimpleStringProperty(param.getValue().getSourcee());
        });
        TableColumn<PacketInfo, String> dstCol = columns.get(3);
        dstCol.setCellValueFactory(param -> {
            return new SimpleStringProperty(param.getValue().getDestination());
        });
        TableColumn<PacketInfo, String> protocolCol = columns.get(4);
        protocolCol.setCellValueFactory(param -> {
            return new SimpleStringProperty(param.getValue().getProtocolName());
        });
        TableColumn<PacketInfo, String> lengthCol = columns.get(5);
        lengthCol.setCellValueFactory(param -> {
            return new SimpleStringProperty(String.valueOf(param.getValue().getLength()));
        });
        TableColumn<PacketInfo, String> infoCol = columns.get(6);
        infoCol.setCellValueFactory(param -> {
            return new SimpleStringProperty(param.getValue().getInfo());
        });

        tableView.setOnMouseClicked(event -> {
            int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
            ObservableList<PacketInfo> items = tableView.getItems();
            if (selectedIndex >= 0 && selectedIndex < items.size()) {
                packetDetailText.setText(items.get(selectedIndex).getPacket().toString());
            }
        });

        // Add radio menu item to toggle group.
        ToggleGroup toggleGroup = new ToggleGroup();
        menuKmeansAlgorithm.getItems().forEach(menuItem -> {
            toggleGroup.getToggles().add((RadioMenuItem) menuItem);
        });

        System.out.println("End initialize");
    }

    /**
     * Open file and load offline *.pcap data
     *
     * @param event
     */
    @FXML
    protected void openFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(Config.getString("label_open_file"));
        fileChooser.setInitialDirectory(new File("."));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("libpcap", "*.pcap")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file == null || !file.exists()) {
            System.err.println("Can't find file:" + file);
            return;
        }

        ObservableList data = tableView.getItems();
        data.clear();
        Config.setTimestamp(Config.DEFAULT_TIMESTAMP);

        // 捕获离线数据包
        PcapManager.getInstance().captureOffline(file, packet -> {
            // 将第一个数据包的时间戳设置为起始时间
            if (Config.getTimestamp() <= Config.DEFAULT_TIMESTAMP) {
                Config.setTimestamp(packet.getCaptureHeader().timestampInMicros());
            }

            PcapPacket packetCopy = new PcapPacket(packet); // 获取副本
            data.add(new PacketInfo(packetCopy));
        });
    }

    @FXML
    protected void switchLanguage(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();
        if (source != null) {
            switch (source.getId()) {
                case "lang_en":
                    break;
                case "lang_zh":
                    break;
            }
        }
    }

    @FXML
    protected void startCapture(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();
        NetworkAdapter networkAdapter = PcapManager.getInstance().getNetworkAdapter();
        if (networkAdapter == null) {
            showCaptureOptions(null);
            return;
        }

        setStatusInfo(Config.getString("label_start_capture"));
        source.setDisable(true);
        menuItemStop.setDisable(false);

        PcapManager.getInstance().captureLive(packet -> {
            // 将第一个数据包的时间戳设置为起始时间
            if (Config.getTimestamp() <= Config.DEFAULT_TIMESTAMP) {
                Config.setTimestamp(packet.getCaptureHeader().timestampInMicros());
            }

            PcapPacket packetCopy = new PcapPacket(packet); // 获取副本
            ObservableList items = tableView.getItems();
            items.add(new PacketInfo(packetCopy));
        });
    }

    @FXML
    protected void stopCapture(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();

        setStatusInfo(Config.getString("label_stop_capture"));
        source.setDisable(true);
        menuItemStart.setDisable(false);

        PcapManager.getInstance().stopCapture();

        setStatusInfo(Config.getString("label_pcap_ready"));
    }

    @FXML
    protected void showCaptureOptions(ActionEvent event) {

        List<PcapIf> interfaces = PcapManager.getInstance().getDeviceList();
        OptionDialog optionDialog = new OptionDialog(interfaces);
        Optional<NetworkAdapter> optional = optionDialog.showAndWait();
        if (optional.isPresent()) {     // If result is not null, start capture immediately
            startCapture(new ActionEvent(menuItemStart, null));
        } else {
            NetworkAdapter networkAdapter = PcapManager.getInstance().getNetworkAdapter();
            if (networkAdapter != null) {
                setStatusInfo(String.format(Config.getString("label_select_xx"), networkAdapter));
            } else {
                setStatusWarning(Config.getString("label_select_nothing"));
            }
        }
    }

    private void setStatusError(String text) {
        setStatusText(text, Paint.valueOf("#F44336"));
    }

    private void setStatusInfo(String text) {
        setStatusText(text, Paint.valueOf("black"));
    }

    private void setStatusWarning(String text) {
        setStatusText(text, Paint.valueOf("#3F61BF"));
    }

    private void setStatusText(String text, Paint paint) {
        statusInfoLabel.setText(text);
        statusInfoLabel.setTextFill(paint);
    }

    @FXML
    protected void exit(ActionEvent event) {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    public void clear(ActionEvent event) {
        tableView.getItems().clear();
    }

    @FXML
    public void showAboutDialog(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.NONE,
                "Author: whinc\n\nE-mail: xiaohui_hubei@163.com\n",
                ButtonType.OK);
        alert.setTitle("About");
        alert.showAndWait();
    }

    @FXML
    public void extractVector(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.NONE, "Waiting...", ButtonType.CLOSE);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.show();

        // 先停止捕获
        stopCapture();

        // 提取网络流行为特征
        ClusterModule.getInstance().extractVector(getPackets());

        appendLog(ClusterModule.getInstance().toString());
        tabPane.getSelectionModel().select(logTab);

        alert.close();
    }

    private List<PacketInfo> getPackets() {
        return tableView.getItems();
    }

    private void appendLog(String log) {
        String datetime = LOG_DATE_FORMAT.format(Calendar.getInstance().getTime());
        logText.appendText(datetime + "\n" + log);
    }

    /**
     * 分析捕获的数据包中不同协议所在的比例，并通过饼状图显示
     */
    @FXML
    public void plotPieChart(ActionEvent event) {
        showChart(pieChart);

        stopCapture();

        pieChart.getData().clear();
        List<PacketInfo> packets = getPackets();
        List<PieChart.Data> dataList = new ArrayList<>();
        double total = 0;
        for (PacketInfo pkt : packets) {
            boolean exsit = false;
            for (PieChart.Data data : dataList) {
                if (data.getName().equals(pkt.getProtocolName())) {
                    data.setPieValue(data.getPieValue() + 1);
                    ++total;
                    exsit = true;
                    break;
                }
            }
            if (!exsit) {
                dataList.add(new PieChart.Data(pkt.getProtocolName(), 1));
                ++total;
            }
        }
        pieChart.getData().addAll(dataList);

        if (!dataList.isEmpty()) {
            // 输出到日志面板
            StringBuilder log = new StringBuilder("Protocol Composition:\n");
            for (PieChart.Data data : dataList) {
                log.append(String.format("%s : %.1f%%\n", data.getName(), data.getPieValue() / total * 100));
            }
            appendLog(log.toString());
        } else {
            appendLog("There is no data.");
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            alert.setHeaderText("There is no data!");
            alert.showAndWait();
        }
    }

    @FXML
    protected void plotScatterChart() {
        showChart(scatterChart);
        scatterChart.getData().clear();

        int k = 4;
        Kmeans kmeans = new Kmeans(k);
        Eigenvector[] dataset = new Eigenvector[10];
        dataset[0] = new Eigenvector(1, 2);
        dataset[1] = new Eigenvector(3, 3);
        dataset[2] = new Eigenvector(3, 4);
        dataset[3] = new Eigenvector(5, 6);
        dataset[4] = new Eigenvector(8, 9);
        dataset[5] = new Eigenvector(4, 5);
        dataset[6] = new Eigenvector(6, 4);
        dataset[7] = new Eigenvector(3, 9);
        dataset[8] = new Eigenvector(5, 9);
        dataset[9] = new Eigenvector(4, 2);
        dataset[9] = new Eigenvector(1, 9);
        dataset[9] = new Eigenvector(7, 8);
        kmeans.setSampleSet(dataset);
        kmeans.run();

        List<List<Eigenvector>> centerList = kmeans.getCenterList();
        for (int i = 0; i < centerList.size(); ++i) {
            XYChart.Series<Object, Object> series = new XYChart.Series<>();
            series.setName("Cluster" + (i+1));
            List<Eigenvector> sampleList = centerList.get(i);
            for (Eigenvector v : sampleList) {
                series.getData().add(new XYChart.Data<>(v.get(0), v.get(1)));
            }
            scatterChart.getData().add(series);
        }
    }

    /**
     * 显示图表，所有图表都调往该方法显示，可以确保当前只有一个图标对用户可见
     * @param chart
     */
    private void showChart(Chart chart) {
        if (chart == null) return;

        if (curVisibleChart == null || curVisibleChart != chart) {
            if (curVisibleChart != null) {
                curVisibleChart.setVisible(false);
            }
            chart.setVisible(true);
            curVisibleChart = chart;
        }
    }

    private void stopCapture() {
        stopCapture(new ActionEvent(menuItemStop, null));
    }
}
