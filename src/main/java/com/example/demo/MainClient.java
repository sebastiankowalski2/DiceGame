package com.example.demo;

import javafx.animation.TranslateTransition;
import javafx.util.Duration;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainClient extends Application {
    private BufferedReader in;
    private PrintWriter out;
    private Stage primaryStage;
    private Label rollResultLabel;
    private Label player1ScoreLabel;
    private Label player2ScoreLabel;
    private Label gameStatusLabel;
    private Button rollButton;
    private VBox joinRoomVBox;
    private ImageView[] diceImageViews;
    private boolean[] diceSelected;
    private TableView<ScoreRow> scoreTable;

    private String playerName;
    private String opponentName;
    private int playerNumber;
    public String who_am_i;
    private Set<Integer> availableRooms = new HashSet<>();
    private ExecutorService executorService;
    private int rollCount;
    private int[] diceValues = new int[5];
    private boolean isMyTurn;
    public boolean hasRolledDice;
    private Set<String> assignedCategories = new HashSet<>();
    private boolean assignButtonClicked;
    private int turnsCounter; // New variable for counting turns

    public static void main(String[] args) {
        launch(args);
    }

    private Image[] diceImages;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        executorService = Executors.newCachedThreadPool();
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/logi.png")));

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        // Load dice images
        diceImages = new Image[6];
        for (int i = 0; i < 6; i++) {
            diceImages[i] = new Image(getClass().getResourceAsStream("/k" + (i + 1) + ".png"));
        }
        showLoginScene();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void showLoginScene() {
        VBox loginRoot = new VBox();
        loginRoot.setAlignment(Pos.CENTER);
        loginRoot.setSpacing(10);
        loginRoot.setStyle("-fx-background-color: #794108;");

        loginRoot.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Load CSS style

        Scene loginScene = new Scene(loginRoot, 300, 200);

        Text usernameText = new Text("Enter your username");
        loginRoot.getChildren().add(usernameText);
        usernameText.setStyle("-fx-fill: #FFFFFF; -fx-font-size: 16px;");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name");
        nameField.setMaxWidth(200);
        nameField.setStyle("-fx-border-color: black; -fx-border-width: 2px; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        Button connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #000000; -fx-text-fill: #FFFFFF; -fx-font-size: 16px;");

        connectButton.setOnAction(event -> {
            try {
                playerName = nameField.getText();
                if (playerName.isEmpty()) {
                    showAlert("Name cannot be empty");
                    return;
                }
                Socket socket = new Socket("localhost", 12345);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println(playerName);

                executorService.submit(() -> {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) {
                            String finalMessage = message;
                            Platform.runLater(() -> handleServerMessage(finalMessage));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                showMainScene();
                primaryStage.getIcons().clear();
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/door.png")));


            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        loginRoot.getChildren().addAll(nameField, connectButton);
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("Login");
        primaryStage.show();
    }

    private void showMainScene() {
        Platform.runLater(() -> {
            VBox mainRoot = new VBox();
            mainRoot.setAlignment(Pos.CENTER);
            mainRoot.setSpacing(10);
            mainRoot.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Load CSS style

            mainRoot.setStyle("-fx-background-color: #794108;");

            Scene mainScene = new Scene(mainRoot, 400, 300);

            Text roomText = new Text("Create a room");
            mainRoot.getChildren().add(roomText);
            roomText.setStyle("-fx-fill: #FFFFFF; -fx-font-size: 16px;");

            TextField roomField = new TextField();
            roomField.setMaxWidth(100);
            roomField.setPromptText("room ID");

            Text joinText = new Text("or enter room ID to join");
            joinText.setStyle("-fx-fill: #FFFFFF; -fx-font-size: 16px;");

            Button createRoomButton = new Button("Create Room");
            Button joinRoomButton = new Button("Join Room");

            joinRoomVBox = new VBox();
            joinRoomVBox.setAlignment(Pos.CENTER);
            joinRoomVBox.setSpacing(5);

            createRoomButton.setOnAction(event -> {
                out.println("create");
            });

            joinRoomButton.setOnAction(event -> {
                String roomId = roomField.getText();
                if (roomId.isEmpty()) {
                    showAlert("Room ID cannot be empty");
                    return;
                }
                out.println("join " + roomId);
            });

            mainRoot.getChildren().addAll(createRoomButton, joinText, roomField, joinRoomButton, joinRoomVBox);
            primaryStage.setScene(mainScene);
            primaryStage.setTitle("Dice Game Client");
        });
    }

    private ImageView shakeImageView;

    private void showGameScene() {
        MainClient client = this;
        Platform.runLater(() -> {
            VBox gameRoot = new VBox();
            gameRoot.setAlignment(Pos.CENTER);
            gameRoot.setSpacing(10);
            gameRoot.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Load CSS style
            gameRoot.setStyle("-fx-background-color: #794108;");
            Scene gameScene = new Scene(gameRoot, 600, 780);

//            Text nothingText = new Text("");
//            Text nothingText2 = new Text("");
            rollResultLabel = new Label("Roll result: ");
            player1ScoreLabel = new Label(playerNumber == 1 ? "You - " + playerName : "Opponent - " + opponentName);
            player2ScoreLabel = new Label(playerNumber == 2 ? "You - " + playerName : "Opponent - " + opponentName);

            //

            gameStatusLabel = new Label();
            rollButton = new Button("Roll Dice");

            rollCount = 0;
            diceImageViews = new ImageView[5];
            diceSelected = new boolean[5];
            HBox diceBox = new HBox(10);
            diceBox.setAlignment(Pos.CENTER);
            for (int i = 0; i < 5; i++) {
                VBox diceContainer = new VBox(5);
                diceImageViews[i] = new ImageView();
                diceImageViews[i].setFitWidth(50);
                diceImageViews[i].setFitHeight(50);
                int finalI = i;
                diceImageViews[i].setOnMouseClicked(event -> {
                    diceSelected[finalI] = !diceSelected[finalI];
                    if (diceSelected[finalI]) {
                        diceImageViews[finalI].setStyle("-fx-effect: dropshadow(gaussian, yellow, 10, 0.5, 0, 0);");
                    } else {
                        diceImageViews[finalI].setStyle("");
                    }
                });
                diceContainer.getChildren().add(diceImageViews[i]);
                diceBox.getChildren().add(diceContainer);
                diceValues[i] = 0;
                diceSelected[i] = false;
            }

            rollButton.setOnAction(event -> {
                rollCount++;
                if (rollCount > 3) {
                    showAlert("You can only roll three times per turn.");
                    return;
                }

                boolean anySelected = false;
                for (boolean selected : diceSelected) {
                    if (selected) {
                        anySelected = true;
                        break;
                    }
                }

                StringBuilder rollCommand = new StringBuilder("roll");
                for (int i = 0; i < 5; i++) {
                    if (rollCount == 1 || !anySelected || diceSelected[i]) {
                        int score = (int) (Math.random() * 6) + 1;
                        diceValues[i] = score;
                    }
                    rollCommand.append(" ").append(diceValues[i]);
                }
                updateDiceLabels();
                out.println(rollCommand.toString());
                hasRolledDice = true;
                resetDiceSelections(); // Reset dice selections after rolling

                // Trigger shake animation
                triggerShakeAnimation();
            });

            scoreTable = new TableView<>();
            scoreTable.setMinHeight(469);
            scoreTable.setEditable(true);
            scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            if (playerNumber == 1) { //zaktualizowany kawalek kodu ktory ma zostac w tej postaci

                TableColumn<ScoreRow, String> categoryColumn = new TableColumn<>("Category");
                categoryColumn.setCellValueFactory(cellData -> cellData.getValue().categoryProperty());

                TableColumn<ScoreRow, Number> player1Column = new TableColumn<>(playerName);
                player1Column.setCellValueFactory(cellData -> cellData.getValue().player1ScoreProperty());

                TableColumn<ScoreRow, Number> player2Column = new TableColumn<>(opponentName);
                player2Column.setCellValueFactory(cellData -> cellData.getValue().player2ScoreProperty());

                TableColumn<ScoreRow, Button> assignButtonColumn = new TableColumn<>("Assign");
                assignButtonColumn.setCellValueFactory(cellData -> cellData.getValue().assignButtonProperty());

                scoreTable.getColumns().addAll(categoryColumn, player1Column, player2Column, assignButtonColumn);
                populateScoreTable();
            } else {
                TableColumn<ScoreRow, String> categoryColumn = new TableColumn<>("Category");
                categoryColumn.setCellValueFactory(cellData -> cellData.getValue().categoryProperty());

                TableColumn<ScoreRow, Number> player1Column = new TableColumn<>(opponentName);
                player1Column.setCellValueFactory(cellData -> cellData.getValue().player1ScoreProperty());

                TableColumn<ScoreRow, Number> player2Column = new TableColumn<>(playerName);
                player2Column.setCellValueFactory(cellData -> cellData.getValue().player2ScoreProperty());

                TableColumn<ScoreRow, Button> assignButtonColumn = new TableColumn<>("Assign");
                assignButtonColumn.setCellValueFactory(cellData -> cellData.getValue().assignButtonProperty());

                scoreTable.getColumns().addAll(categoryColumn, player1Column, player2Column, assignButtonColumn);
                populateScoreTable();
            }

            shakeImageView = new ImageView(new Image(getClass().getResourceAsStream("/dice.png")));
            shakeImageView.setFitWidth(50);
            shakeImageView.setFitHeight(50);
            shakeImageView.getStyleClass().add("shake-image");
            shakeImageView.setLayoutX(0);
            shakeImageView.setLayoutY(0);
            gameRoot.getChildren().addAll(player1ScoreLabel, player2ScoreLabel, rollResultLabel, diceBox, rollButton, gameStatusLabel, scoreTable);
            gameRoot.getChildren().add(0, shakeImageView);
            primaryStage.setScene(gameScene);
            if(playerNumber == 1){
                primaryStage.setTitle("Dice Game - Your turn");
            }
            else primaryStage.setTitle("Dice Game - Opponent's turn");
        });
    }

    private void triggerShakeAnimation() {
        TranslateTransition translateTransition = new TranslateTransition(Duration.millis(100), shakeImageView);
        translateTransition.setByX(10);
        translateTransition.setCycleCount(6);
        translateTransition.setAutoReverse(true);
        translateTransition.play();
    }


    private void resetDiceSelections() {
        for (int i = 0; i < diceSelected.length; i++) {
            diceSelected[i] = false;
            diceImageViews[i].setStyle("");
        }
    }


    private void handleServerMessage(String message) {
        if (message.startsWith("Room created with ID")) {
            out.println("join " + message.split(": ")[1]);
        }

        if (message.startsWith("Player number")) {
            playerNumber = Integer.parseInt(message.split(" ")[2]);
        }

        if (message.startsWith("You are player number:")) {
            who_am_i = message.split(":")[1].trim();
            System.out.println("Assigned as player number: " + who_am_i);
        }

        if (message.startsWith("Opponent name")) {
            opponentName = message.split(": ")[1];
        }

        if (message.startsWith("You are player number:")) {
            playerNumber = Integer.parseInt(message.split(":")[1].trim());
            System.out.println("Assigned as player number: " + playerNumber);
        }

        if (message.startsWith("Game started")) {
            showGameScene();
            primaryStage.getIcons().clear();
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/dice.png")));

            if (message.contains("Your turn")) {
                primaryStage.setTitle("Dice Game - Your turn");
                isMyTurn = true;
                rollCount = 0;
                hasRolledDice = false;
                assignButtonClicked = false;
                enableRollButton(true);
            } else {
                primaryStage.setTitle("Dice Game - Opponent's turn");
                isMyTurn = false;
                enableRollButton(false);
            }
        }

        if (message.startsWith("Your turn.")) {
            primaryStage.setTitle("Dice Game - Your turn");
            isMyTurn = true;
            rollCount = 0;
            hasRolledDice = false;
            assignButtonClicked = false;
            resetDiceValues();
            enableRollButton(true);
        } else if (message.startsWith("Opponent's turn.")) {
            primaryStage.setTitle("Dice Game - Opponent's turn");
            resetDiceValues();
            isMyTurn = false;
            enableRollButton(false);
        }

        if (message.startsWith("Roll result")) {
            String[] parts = message.split(": ");
            String playerName = parts[1].split(" scored")[0];
            String[] scores = parts[1].split(" scored ")[1].replaceAll("[\\[\\]]", "").split(", ");
            updateScores(playerName, scores);
        }

        if (message.startsWith("New room available:")) {
            int roomId = Integer.parseInt(message.split(": ")[1]);
            if (!availableRooms.contains(roomId)) {
                availableRooms.add(roomId);
                updateRoomList();
            }
        }

        if (message.startsWith("Room not found")) {
            showAlert("Room not found.");
        }

        if (message.startsWith("Joined room")) {
            showAlert("Successfully joined room.");
        }

        if (message.startsWith("Assigned")) {
            String[] parts = message.split(" ");
            String category = parts[1];
            int player1Score = Integer.parseInt(parts[2]);
            int player2Score = Integer.parseInt(parts[3]);
            updateScoreTable(category, player1Score, player2Score);
            turnsCounter++;  // Increment turn counter
            checkGameEnd();  // Check if the game should end
        }

    }

    private void enableRollButton(boolean enable) {
        Platform.runLater(() -> {
            rollButton.setDisable(!enable);
        });
    }

    private void updateScores(String playerName, String[] scores) {
        Platform.runLater(() -> {
            for (int i = 0; i < diceValues.length; i++) {
                diceValues[i] = Integer.parseInt(scores[i]);
            }
            updateDiceLabels();
        });
    }

    public void updateDiceLabels() {
        Platform.runLater(() -> {
            for (int i = 0; i < diceImageViews.length; i++) {
                int diceValue = diceValues[i];
                if (diceValue < 1 || diceValue > 6) {
                    diceImageViews[i].setImage(null); // Handle invalid dice value
                } else {
                    diceImageViews[i].setImage(diceImages[diceValue - 1]);
                }
            }
        });
    }

    public void resetDiceValues() {
        Platform.runLater(() -> {
            for (int i = 0; i < diceValues.length; i++) {
                diceValues[i] = 0;
            }
            Arrays.fill(diceSelected, false);
            updateDiceLabels();
        });
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void updateRoomList() {
        Platform.runLater(() -> {
            joinRoomVBox.getChildren().clear();
            for (int roomId : availableRooms) {
                Label roomLabel = new Label("Room ID: " + roomId);
                roomLabel.setOnMouseClicked(event -> {
                    out.println("join " + roomId);
                });
                joinRoomVBox.getChildren().add(roomLabel);
            }
        });
    }

    private void populateScoreTable() {
        scoreTable.setFixedCellSize(34); // Set preferred row height to 30 pixels

        scoreTable.getItems().addAll(
                new ScoreRow("1", this),
                new ScoreRow("2", this),
                new ScoreRow("3", this),
                new ScoreRow("4", this),
                new ScoreRow("5", this),
                new ScoreRow("6", this),
                new ScoreRow("Three_of_kind", this),
                new ScoreRow("Four_of_kind", this),
                new ScoreRow("Small_Strit", this),
                new ScoreRow("Large_Strit", this),
                new ScoreRow("Full", this),
                new ScoreRow("General", this),
                new ScoreRow("Chance", this)
        );
    }

    public int calculateCurrentCategorySum(String category) {
        switch (category) {
            case "1":
            case "2":
            case "3":
            case "4":
            case "5":
            case "6":
                int targetValue = Integer.parseInt(category);
                return Arrays.stream(diceValues).filter(value -> value == targetValue).sum();
            case "Three_of_kind":
                return checkNOfAKind(3) ? Arrays.stream(diceValues).sum() : 0;
            case "Four_of_kind":
                return checkNOfAKind(4) ? Arrays.stream(diceValues).sum() : 0;
            case "Small_Strit":
                return checkStraight(4) ? 30 : 0;
            case "Large_Strit":
                return checkStraight(5) ? 40 : 0;
            case "Full":
                return checkFullHouse() ? 25 : 0;
            case "General":
                return checkNOfAKind(5) ? 50 : 0;
            case "Chance":
                return Arrays.stream(diceValues).sum();
            default:
                return 0;
        }
    }

    private boolean checkNOfAKind(int n) {
        for (int value : diceValues) {
            if (countOccurrences(value) >= n) {
                return true;
            }
        }
        return false;
    }

    private boolean checkFullHouse() {
        boolean hasThreeOfAKind = false;
        boolean hasPair = false;
        for (int value : diceValues) {
            int count = countOccurrences(value);
            if (count == 3) {
                hasThreeOfAKind = true;
            } else if (count == 2) {
                hasPair = true;
            }
        }
        return hasThreeOfAKind && hasPair;
    }

    private boolean checkStraight(int length) {
        Set<Integer> uniqueValues = Arrays.stream(diceValues).boxed().collect(Collectors.toSet());
        if (length == 4) {
            return uniqueValues.containsAll(Arrays.asList(1, 2, 3, 4)) ||
                    uniqueValues.containsAll(Arrays.asList(2, 3, 4, 5)) ||
                    uniqueValues.containsAll(Arrays.asList(3, 4, 5, 6));
        } else if (length == 5) {
            return uniqueValues.containsAll(Arrays.asList(1, 2, 3, 4, 5)) ||
                    uniqueValues.containsAll(Arrays.asList(2, 3, 4, 5, 6));
        }
        return false;
    }

    private int countOccurrences(int value) {
        int count = 0;
        for (int diceValue : diceValues) {
            if (diceValue == value) {
                count++;
            }
        }
        return count;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void disableRollButton() {
        enableRollButton(false);
    }

    public void endTurn() {
        StringBuilder endTurnCommand = new StringBuilder("endturn");
        for (int value : diceValues) {
            endTurnCommand.append(" ").append(value);
        }
        out.println(endTurnCommand.toString());
        resetDiceValues();
    }

    public boolean isCategoryAssigned(String category) {
        return assignedCategories.contains(category);
    }

    public void addAssignedCategory(String category) {
        assignedCategories.add(category);
    }

    public boolean isAssignButtonClicked() {
        return assignButtonClicked;
    }

    public void setAssignButtonClicked(boolean assignButtonClicked) {
        this.assignButtonClicked = assignButtonClicked;
    }

    public void updateScoreTable(String category, int player1Score, int player2Score) {
        for (ScoreRow row : scoreTable.getItems()) {
            if (row.getCategory().equals(category)) {
                row.setPlayer1Score(player1Score);
                row.setPlayer2Score(player2Score);
                break;
            }
        }
    }

    public PrintWriter getOut() {
        return out;
    }

    // Check if the game should end
    private void checkGameEnd() {
        if (turnsCounter >= 26) {  // Total of 26 turns, 13 per player
            Platform.runLater(() -> showGameEndDialog());
        }
    }

    // Show game end dialog
    private void showGameEndDialog() {
        if (playerNumber == 1) {

            int player1TotalScore = scoreTable.getItems().stream().mapToInt(ScoreRow::getPlayer1Score).sum();
            int player2TotalScore = scoreTable.getItems().stream().mapToInt(ScoreRow::getPlayer2Score).sum();
            String winnerMessage = "Game Over! " +
                    (player1TotalScore > player2TotalScore ? playerName + " Wins!" : opponentName + " Wins!") +
                    "\nScores:\n" + playerName + ": " + player1TotalScore + "\n" + opponentName + ": " + player2TotalScore;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText(null);
            alert.setContentText(winnerMessage);

            alert.setOnHidden(event -> {
                Platform.exit();
                System.exit(0);
            });

            alert.showAndWait();

        } else {

            int player1TotalScore = scoreTable.getItems().stream().mapToInt(ScoreRow::getPlayer1Score).sum();
            int player2TotalScore = scoreTable.getItems().stream().mapToInt(ScoreRow::getPlayer2Score).sum();
            String winnerMessage = "Game Over! " +
                    (player1TotalScore > player2TotalScore ? opponentName + " Wins!" : playerName + " Wins!") +
                    "\nScores:\n" + opponentName + ": " + player1TotalScore + "\n" + playerName + ": " + player2TotalScore;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText(null);
            alert.setContentText(winnerMessage);

            alert.setOnHidden(event -> {
                Platform.exit();
                System.exit(0);
            });

            alert.showAndWait();

        }

        // Disable roll button and other game actions
        rollButton.setDisable(true);
        scoreTable.setDisable(true);
    }
}

class ScoreRow {
    private final StringProperty category;
    private final IntegerProperty player1Score;
    private final IntegerProperty player2Score;
    private final ObjectProperty<Button> assignButton;

    public ScoreRow(String category, MainClient client) {
        this.category = new SimpleStringProperty(category);
        this.player1Score = new SimpleIntegerProperty(0);
        this.player2Score = new SimpleIntegerProperty(0);
        this.assignButton = new SimpleObjectProperty<>(new Button("Assign"));

        assignButton.get().setOnAction(event -> {
            if (!client.isCategoryAssigned(category) && !client.isAssignButtonClicked() && client.hasRolledDice) {
                int currentSum = client.calculateCurrentCategorySum(category);
                if (client.getPlayerNumber() == 1) {
                    player1Score.set(currentSum);
                } else {
                    player2Score.set(currentSum);
                }
                assignButton.get().setDisable(true);
                client.addAssignedCategory(category);
                client.setAssignButtonClicked(true);
                client.resetDiceValues();
                client.disableRollButton();
                client.endTurn();
                client.getOut().println(String.format("assign %s %d ", category, currentSum));
            }
        });

        assignButton.get().setDisable(client.isCategoryAssigned(category));
    }

    public String getCategory() {
        return category.get();
    }

    public void setPlayer1Score(int score) {
        player1Score.set(score);
    }

    public void setPlayer2Score(int score) {
        player2Score.set(score);
    }

    public int getPlayer1Score() {
        return player1Score.get();
    }

    public int getPlayer2Score() {
        return player2Score.get();
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public IntegerProperty player1ScoreProperty() {
        return player1Score;
    }

    public IntegerProperty player2ScoreProperty() {
        return player2Score;
    }

    public ObjectProperty<Button> assignButtonProperty() {
        return assignButton;
    }
}

