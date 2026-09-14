import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Scanner;

public class App {
    private static final int SIZE = 10;
    private static final int START_ROW = 1;
    private static final int START_COLUMN = 1;
    private static final int TARGET_ROW = 8;
    private static final int TARGET_COLUMN = 8;
    private static final int WALL = -1;
    private static final int INF = Integer.MAX_VALUE;

    private static final int[][] DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    };

    private record Node(int row, int column, int cost) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            return Integer.compare(cost, other.cost);
        }
    }

    public static void main(String[] args) {
        int mapVersion = chooseMap(args);
        int[][] terrainCost = createMap(mapVersion);

        int[][] distance = new int[SIZE][SIZE];
        int[][] previousRow = new int[SIZE][SIZE];
        int[][] previousColumn = new int[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            Arrays.fill(distance[row], INF);
            Arrays.fill(previousRow[row], -1);
            Arrays.fill(previousColumn[row], -1);
        }

        dijkstra(terrainCost, distance, previousRow, previousColumn);
        char[][] display = createDisplay(terrainCost, previousRow, previousColumn);

        System.out.println("Dijkstra 2D Grid 8-way");
        System.out.println("Map version: " + mapVersion);
        System.out.println("Start: (1,1) | Target: (8,8)");
        System.out.println("Move cost: 4-way = 1, diagonal = 2");
        System.out.println("Terrain cost multiplier: 1 = grass, 3 = mud, 6 = sand, # = wall");
        printGrid(display);
        System.out.println("Minimum cost: " + distance[TARGET_ROW][TARGET_COLUMN]);
    }

    private static int chooseMap(String[] args) {
        if (args.length > 0) {
            try {
                int mapVersion = Integer.parseInt(args[0]);
                if (mapVersion >= 1 && mapVersion <= 3) {
                    return mapVersion;
                }
            } catch (NumberFormatException ignored) {
                // Use the interactive menu when the argument is invalid.
            }
        }

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Pilih versi map:");
            System.out.println("1. Map 1 - Kosong");
            System.out.println("2. Map 2 - Dinding U");
            System.out.println("3. Map 3 - Dinding U + terrain cost");
            System.out.print("Pilihan (1-3): ");
            while (scanner.hasNextInt()) {
                int mapVersion = scanner.nextInt();
                if (mapVersion >= 1 && mapVersion <= 3) {
                    return mapVersion;
                }
                System.out.print("Pilihan harus 1, 2, atau 3: ");
            }
        }
        return 1;
    }

    private static int[][] createMap(int mapVersion) {
        int[][] terrainCost;
        switch (mapVersion) {
            case 1 -> {
                terrainCost = createEmptyTerrain();
            }
            case 2 -> {
                terrainCost = createEmptyTerrain();
                addUShapedWall(terrainCost, 3, 3, 6, 6);
            }
            default -> {
                terrainCost = new int[][] {
                    {1, 3, 1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 3, 6, 3, 3, 1, 1, 1, 1, 1},
                    {1, 3, 3, 3, 6, 1, 3, 3, 1, 1},
                    {1, 1, 1, 1, 1, 1, 3, 1, 1, 1},
                    {1, 1, 1, 1, 3, 1, 3, 1, 1, 1},
                    {1, 1, 1, 1, 6, 1, 3, 1, 1, 1},
                    {1, 1, 1, 1, 3, 1, 3, 1, 1, 1},
                    {1, 1, 1, 1, 3, 1, 3, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
                };
                addUShapedWall(terrainCost, 3, 3, 6, 6);
            }
        }
        return terrainCost;
    }

    private static int[][] createEmptyTerrain() {
        return new int[][] {
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        };
    }

    private static void addUShapedWall(int[][] costs, int topRow,
            int leftColumn, int bottomRow, int rightColumn) {
        for (int column = leftColumn; column <= rightColumn; column++) {
            costs[bottomRow][column] = WALL;
        }
        for (int row = topRow; row <= bottomRow; row++) {
            costs[row][leftColumn] = WALL;
            costs[row][rightColumn] = WALL;
        }
    }

    private static void dijkstra(int[][] terrainCost, int[][] distance,
            int[][] previousRow, int[][] previousColumn) {
        PriorityQueue<Node> queue = new PriorityQueue<>();
        distance[START_ROW][START_COLUMN] = 0;
        queue.add(new Node(START_ROW, START_COLUMN, 0));

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.cost != distance[current.row][current.column]) {
                continue;
            }
            if (current.row == TARGET_ROW && current.column == TARGET_COLUMN) {
                return;
            }

            for (int[] direction : DIRECTIONS) {
                int nextRow = current.row + direction[0];
                int nextColumn = current.column + direction[1];
                if (!isInside(nextRow, nextColumn) || terrainCost[nextRow][nextColumn] == WALL) {
                    continue;
                }

                boolean diagonalMove = direction[0] != 0 && direction[1] != 0;
                int moveCost = diagonalMove ? 2 : 1;
                int nextCost = current.cost + moveCost * terrainCost[nextRow][nextColumn];
                if (nextCost < distance[nextRow][nextColumn]) {
                    distance[nextRow][nextColumn] = nextCost;
                    previousRow[nextRow][nextColumn] = current.row;
                    previousColumn[nextRow][nextColumn] = current.column;
                    queue.add(new Node(nextRow, nextColumn, nextCost));
                }
            }
        }
    }

    private static char[][] createDisplay(int[][] terrainCost,
            int[][] previousRow, int[][] previousColumn) {
        char[][] display = new char[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                display[row][column] = terrainCost[row][column] == WALL
                        ? '#'
                        : Character.forDigit(terrainCost[row][column], 10);
            }
        }

        int row = TARGET_ROW;
        int column = TARGET_COLUMN;
        while (row != -1 && column != -1) {
            display[row][column] = '.';
            int previous = previousRow[row][column];
            column = previousColumn[row][column];
            row = previous;
        }
        display[START_ROW][START_COLUMN] = 'S';
        display[TARGET_ROW][TARGET_COLUMN] = 'T';
        return display;
    }

    private static boolean isInside(int row, int column) {
        return row >= 0 && row < SIZE && column >= 0 && column < SIZE;
    }

    private static void printGrid(char[][] grid) {
        for (char[] row : grid) {
            for (char cell : row) {
                System.out.print(cell + " ");
            }
            System.out.println();
        }
    }
}

