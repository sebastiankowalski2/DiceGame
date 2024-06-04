package com.example.demo;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainServer {
    private static final AtomicInteger roomIDCounter = new AtomicInteger(1);
    private static final Map<Integer, GameRoom> gameRooms = Collections.synchronizedMap(new HashMap<>());
    private static final List<PlayerHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static Connection dbConnection;

    public static void main(String[] args) {
        System.out.println("The dice game server is running...");
        try {
            initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        try (ServerSocket listener = new ServerSocket(12345)) {
            while (true) {
                Socket clientSocket = listener.accept();
                threadPool.execute(new PlayerHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static void initializeDatabase() throws SQLException {
        try {
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306", "root", "");
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS dicegame");
                stmt.executeUpdate("USE dicegame");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS points ("
                        + "username VARCHAR(50), "
                        + "`1` INT, "
                        + "`2` INT, "
                        + "`3` INT, "
                        + "`4` INT, "
                        + "`5` INT, "
                        + "`6` INT, "
                        + "Three_of_kind INT, "
                        + "Four_of_kind INT, "
                        + "Small_Strit INT, "
                        + "Large_Strit INT, "
                        + "Full INT, "
                        + "General INT, "
                        + "Chance INT, "
                        + "PRIMARY KEY (username))");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_throw ("
                        + "username VARCHAR(50), "
                        + "throw VARCHAR(50), "
                        + "PRIMARY KEY (username, throw))");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS score ("
                        + "username VARCHAR(50), "
                        + "final_score INT, "
                        + "table_number INT, "
                        + "result VARCHAR(10), "
                        + "PRIMARY KEY (username))");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }


    static class PlayerHandler implements Runnable {
        private final Socket socket;
        private String name;
        private BufferedReader in;
        private PrintWriter out;
        private GameRoom currentRoom;
        private int playerNumber;

        public PlayerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read player name
                name = in.readLine();
                createPlayerRow(name); // Create row for the player if it doesn't exist
                clients.add(this);

                // Handle client commands
                while (true) {
                    String command = in.readLine();
                    if (command == null) {
                        break;
                    }

                    if (command.startsWith("create")) {
                        createRoom();
                    } else if (command.startsWith("join")) {
                        joinRoom(Integer.parseInt(command.split(" ")[1]));
                    } else if (command.startsWith("endturn")) {
                        handleEndTurn(command);
                    } else if (command.startsWith("assign")) {
                        handleAssignScore(command);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clients.remove(this);
            }
        }


        private void createRoom() {
            GameRoom room = new GameRoom(roomIDCounter.getAndIncrement());
            gameRooms.put(room.getRoomID(), room);
            out.println("Room created with ID: " + room.getRoomID());
            broadcastAvailableRooms();
        }

        private void joinRoom(int roomID) {
            GameRoom room = gameRooms.get(roomID);
            if (room != null) {
                playerNumber = room.addPlayer(this);
                if (playerNumber == -1) {
                    out.println("Room is full.");
                } else {
                    currentRoom = room;
                    out.println("You are player number: " + playerNumber);
                    room.broadcastPlayerNames();
                    if (room.isFull()) {
                        room.startGame();
                    }
                }
            } else {
                out.println("Room not found.");
            }
        }

        private void handleEndTurn(String command) {
            String[] parts = command.split(" ");
            int[] rolls = new int[5];
            for (int i = 1; i <= 5; i++) {
                rolls[i - 1] = Integer.parseInt(parts[i]);
            }
            if (currentRoom != null) {
                currentRoom.endTurn(this, rolls, playerNumber);
                savePlayerThrow(name, rolls);
            }
        }

        private void handleAssignScore(String command) {
            String[] parts = command.split(" ");
            String category = parts[1];
            int score = Integer.parseInt(parts[2]);
            if (currentRoom != null) {
                currentRoom.assignScore(this, category, score);
                savePlayerScore(name, category, score);
            }
        }

        private void broadcastAvailableRooms() {
            synchronized (clients) {
                String message = "New room available: ";
                for (PlayerHandler client : clients) {
                    for (GameRoom room : gameRooms.values()) {
                        if (!room.isFull()) {
                            client.out.println(message + room.getRoomID());
                        }
                    }
                }
            }
        }

        private void savePlayerThrow(String username, int[] rolls) {
            String throwResult = Arrays.toString(rolls);
            try (PreparedStatement pstmt = dbConnection.prepareStatement("INSERT INTO player_throw (username, throw) VALUES (?, ?)")) {
                pstmt.setString(1, username);
                pstmt.setString(2, throwResult);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void createPlayerRow(String username) {
            try (PreparedStatement pstmt = dbConnection.prepareStatement(
                    "INSERT INTO points (username, `1`, `2`, `3`, `4`, `5`, `6`, Three_of_kind, Four_of_kind, Small_Strit, Large_Strit, Full, General, Chance) " +
                            "VALUES (?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL) " +
                            "ON DUPLICATE KEY UPDATE username = username")) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


        private void savePlayerScore(String username, String category, int score) {
            try (PreparedStatement pstmt = dbConnection.prepareStatement(
                    "UPDATE points SET `" + category + "` = ? WHERE username = ?")) {
                pstmt.setInt(1, score);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


    }

    static class GameRoom {
        private final int roomID;
        private final PlayerHandler[] players = new PlayerHandler[2];
        private final int[] scores = {0, 0};
        private int currentPlayerIndex;
        private final Lock turnLock = new ReentrantLock();
        private final int[] diceValues = new int[5];
        private final Map<String, Integer> player1Scores = new HashMap<>();
        private final Map<String, Integer> player2Scores = new HashMap<>();
        private int turnCounter = 0;
        private static final int MAX_TURNS = 24;

        public GameRoom(int roomID) {
            this.roomID = roomID;
        }

        public int getRoomID() {
            return roomID;
        }

        public int addPlayer(PlayerHandler player) {
            if (players[0] == null) {
                players[0] = player;
                return 1;
            } else if (players[1] == null) {
                players[1] = player;
                return 2;
            } else {
                return -1;
            }
        }

        public boolean isFull() {
            return players[0] != null && players[1] != null;
        }

        public void broadcastPlayerNames() {
            if (isFull()) {
                players[0].out.println("Opponent name: " + players[1].name);
                players[1].out.println("Opponent name: " + players[0].name);
            }
        }

        public void startGame() {
            currentPlayerIndex = 0;
            Arrays.fill(diceValues, 0);
            players[currentPlayerIndex].out.println("Game started. Your turn.");
            players[1 - currentPlayerIndex].out.println("Game started. Opponent's turn.");
        }

        private Map<Integer, Integer> calculateScores(int[] diceValues) {
            Map<Integer, Integer> scoreMap = new HashMap<>();
            for (int i = 1; i <= 6; i++) {
                scoreMap.put(i, 0);
            }
            for (int value : diceValues) {
                scoreMap.put(value, scoreMap.get(value) + value);
            }
            return scoreMap;
        }

        public void endTurn(PlayerHandler player, int[] rolls, int playerNumber) {
            turnLock.lock();
            try {
                int playerIndex = player.playerNumber - 1;
                if (playerIndex == currentPlayerIndex) {
                    System.arraycopy(rolls, 0, diceValues, 0, rolls.length);
                    Map<Integer, Integer> scoreMap = calculateScores(diceValues);
                    broadcast("Roll result: " + player.name + " scored " + Arrays.toString(diceValues));
                    broadcast("Score Map: " + scoreMap);

                    Arrays.fill(diceValues, 0);

                    currentPlayerIndex = 1 - currentPlayerIndex;
                    players[currentPlayerIndex].out.println("Your turn.");
                    players[1 - currentPlayerIndex].out.println("Opponent's turn.");

                    turnCounter++;
                    if (turnCounter >= MAX_TURNS) {
                        endGame(playerNumber);
                    }
                }
            } finally {
                turnLock.unlock();
            }
        }

        public void assignScore(PlayerHandler player, String category, int score) {
            int playerIndex = player.playerNumber - 1;
            if (playerIndex == 0) {
                player1Scores.put(category, score);
            } else {
                player2Scores.put(category, score);
            }
            broadcastAssignedScores(category, player1Scores.get(category), player2Scores.get(category));
        }

        private void broadcastAssignedScores(String category, Integer player1Score, Integer player2Score) {
            String message = String.format("Assigned %s %d %d", category, player1Score != null ? player1Score : 0, player2Score != null ? player2Score : 0);
            broadcast(message);
        }

        private void broadcast(String message) {
            for (PlayerHandler player : players) {
                player.out.println(message);
            }
        }

        private void endGame(int playerNumber) {
            int player1TotalScore = player1Scores.values().stream().mapToInt(Integer::intValue).sum();
            int player2TotalScore = player2Scores.values().stream().mapToInt(Integer::intValue).sum();
            String winnerMessage = "Game over! ";
            String player1Result, player2Result;

            if (player1TotalScore > player2TotalScore) {
                winnerMessage += "Player 1 (" + players[0].name + ") wins with " + player1TotalScore + " points!";
                player1Result = "winner";
                player2Result = "loser";
            } else if (player2TotalScore > player1TotalScore) {
                winnerMessage += "Player 2 (" + players[1].name + ") wins with " + player2TotalScore + " points!";
                player1Result = "loser";
                player2Result = "winner";
            } else {
                winnerMessage += "It's a tie with both players scoring " + player1TotalScore + " points!";
                player1Result = "tie";
                player2Result = "tie";
            }

            broadcast(winnerMessage);
            saveFinalScore(players[0].name, player1TotalScore, roomID, player1Result);
            saveFinalScore(players[1].name, player2TotalScore, roomID, player2Result);
        }


        private void saveFinalScore(String username, int finalScore, int tableNumber, String result) {
            try (PreparedStatement pstmt = dbConnection.prepareStatement(
                    "INSERT INTO score (username, final_score, table_number, result) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE final_score = ?, table_number = ?, result = ?")) {
                pstmt.setString(1, username);
                pstmt.setInt(2, finalScore);
                pstmt.setInt(3, tableNumber);
                pstmt.setString(4, result);
                pstmt.setInt(5, finalScore);
                pstmt.setInt(6, tableNumber);
                pstmt.setString(7, result);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }
}

