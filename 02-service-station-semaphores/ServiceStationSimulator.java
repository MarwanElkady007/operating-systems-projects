import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * ServiceStationSimulator — Plain English, Light Mode Version
 * Updated:
 *  - Added Car class (producer thread)
 *  - Producer spawns Car threads (continuous stream)
 *  - Pumps acquire pumpsSem (service bay) before starting service
 *  - Improved logging to match assignment wording
 */


 public class ServiceStationSimulator {

    // ---------- Custom Semaphore ----------
    // Synchronization tool used to control access to shared resources in a multithreaded system.
    static class Semaphore {
        private int value;

        // constructor 
        public Semaphore(int initial) {
            if (initial < 0) throw new IllegalArgumentException("Semaphore initial value < 0");
            this.value = initial;
        }
        
        // wait
        // for the waiting thread to acquire the semaphore
        public synchronized void P() throws InterruptedException {
            while (value == 0) wait();
            value--;
        }
        
        //Release
        //for the pump thread to increase the semaphore
        public synchronized void V() {
            value++;
            notify();
        }
    }

    // ---------- Shared Resources ----------
    
    // waiting buffer that stores cars id
    private final Queue<String> queue = new LinkedList<>();

    // Swing model used to show the queue contents in the GUI list (the visual waiting area).
    final DefaultListModel<String> queueListModel = new DefaultListModel<>();

    // GUI Components
    private final JTextArea logArea;
    private final JPanel pumpsPanel;
    private final JList<String> waitingList;

    //
    private Semaphore empty, full, mutex, pumpsSem;
    private Pump[] pumps;
    private volatile boolean running = false;

    
    // constructor
    public ServiceStationSimulator(JTextArea logArea, JPanel pumpsPanel, JList<String> waitingList) {
        this.logArea = logArea;
        this.pumpsPanel = pumpsPanel;
        this.waitingList = waitingList;
    }


    // help log messages to the text area in the GUI
    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ---------- Car (Producer) ----------
    class Car extends Thread {
        
        // car id
        private final String id;
        
        // constructor
        public Car(String id) {
            this.id = id;
            setName("Car-" + id);
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                log(id + " arrived.");
                // wait for space in waiting area
                empty.P();
                // Only ONE thread is allowed to pass this line at any time.
                mutex.P();
                synchronized (queue) {
                    queue.add(id);
                    final String addId = id;
                    SwingUtilities.invokeLater(() -> queueListModel.addElement(addId));
                }
                log(id + " entered waiting queue.");
                mutex.V();
                // signal that a car is available
                full.V();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // If interrupted while waiting, just exit
            }
        }
    }

    public void startSimulation(int waitingCapacityParam, int numPumpsParam, int totalCars,
                                int arrivalIntervalMillis, int serviceMinMillis, int serviceMaxMillis) {
        if (running) return;

        int waitingCapacity = Math.max(1, Math.min(10, waitingCapacityParam));
        this.pumps = null; // reset reference until created
        int numPumps = Math.max(1, numPumpsParam);

        empty = new Semaphore(waitingCapacity);
        full = new Semaphore(0);
        mutex = new Semaphore(1);
        pumpsSem = new Semaphore(numPumps);

        final int pumpsCount = numPumps;

        // setup UI
        pumpsPanel.removeAll();
        int cols = Math.min(4, numPumps);
        pumpsPanel.setLayout(new GridLayout((int)Math.ceil(numPumps / (double)cols), cols, 12, 12));
        pumpsPanel.setBackground(Color.decode("#f9fafb"));


        // creates the Pump threads, initializes their visual pump cards, and starts them running.
        pumps = new Pump[numPumps];
        for (int i = 0; i < numPumps; i++) {
            PumpCard card = new PumpCard(i + 1);
            pumpsPanel.add(card.panel);
            pumps[i] = new Pump(i + 1, card, Math.max(100, serviceMinMillis), Math.max(Math.max(100, serviceMinMillis), serviceMaxMillis));
            pumps[i].start();
        }

        
        //Clears any previous queue contents and GUI model.
        queue.clear();
        queueListModel.clear();
        SwingUtilities.invokeLater(() -> waitingList.setModel(queueListModel));
        running = true;
        log("Simulation started - Waiting capacity: " + waitingCapacity + ", Pumps: " + numPumps + ", Cars: " + totalCars);


        // Producer: spawn Car threads at arrival intervals
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= totalCars; i++) {
                if (!running) break;
                final String carId = "C" + i;
                Car car = new Car(carId);
                car.start();
                try {
                    Thread.sleep(Math.max(0, arrivalIntervalMillis));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // after producing all cars, wait until queue empty and pumps idle
            //It sleeps 400 ms each iteration to avoid busy-waiting (polling interval).
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    
                    // Checks whether the shared queue is empty.
                    boolean queueEmpty;
                    synchronized (queue) { queueEmpty = queue.isEmpty(); }
                    
                    // Determines whether any pump is currently servicing a car:
                    boolean anyBusy = false;
                    Pump[] localPumps = pumps; // avoid race
                    if (localPumps != null) {
                        for (Pump p : localPumps) if (p.isBusy()) { anyBusy = true; break; }
                    }
                    if (queueEmpty && !anyBusy) break;
                }
                running = false;
                // wake pumps that might be waiting on full in order to exit cleanly
                for (int i = 0; i < pumpsCount; i++) full.V();
                log("All cars processed. Simulation completed.");
            }).start();
        }, "Producer");
        producer.start();
    }

    public void stopSimulation() {
        running = false;
        if (full != null && pumps != null) {
            for (int i = 0; i < pumps.length; i++) full.V();
        }
        log("Simulation stopped by user.");
    }

    // ---------- Pump Visual ----------
    static class PumpCard {
        final JPanel panel = new JPanel(new BorderLayout(8,8));
        final JLabel title = new JLabel();
        final JLabel carLabel = new JLabel("-");
        final JLabel bayLabel = new JLabel();
        final JProgressBar progress = new JProgressBar(0,100);

        PumpCard(int id) {
            panel.setBackground(Color.WHITE);
            panel.setBorder(new CompoundBorder(
                    new LineBorder(new Color(0xE5E7EB),1,true),
                    new EmptyBorder(12,12,12,12)
            ));
            title.setText("Pump " + id);
            title.setFont(title.getFont().deriveFont(Font.BOLD,15f));
            title.setForeground(new Color(0x1E293B));

            bayLabel.setText("Bay " + id + " - Free");
            bayLabel.setForeground(new Color(0x6B7280));

            carLabel.setHorizontalAlignment(SwingConstants.CENTER);
            carLabel.setFont(carLabel.getFont().deriveFont(Font.BOLD,16f));
            carLabel.setForeground(new Color(0x111827));

            progress.setStringPainted(true);
            progress.setBackground(new Color(0xF3F4F6));
            progress.setForeground(new Color(0x10B981));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(title, BorderLayout.WEST);
            top.add(bayLabel, BorderLayout.EAST);

            panel.add(top, BorderLayout.NORTH);
            panel.add(carLabel, BorderLayout.CENTER);
            panel.add(progress, BorderLayout.SOUTH);
        }
    }

    // ---------- Pump Thread ----------
    class Pump extends Thread {
        private final int id;
        private final PumpCard card;
        private final Random rnd = new Random();
        private final int serviceMin, serviceMax;
        private volatile boolean busy = false;

        
        Pump(int id, PumpCard card, int serviceMin, int serviceMax) {
            this.id = id;
            this.card = card;
            this.serviceMin = serviceMin;
            this.serviceMax = serviceMax;
            setDaemon(true);
            setName("Pump-" + id);
            setFree();
        }
        boolean isBusy() { return busy; }

        // GUI 
        private void setBusyVisual(String carId) {
            SwingUtilities.invokeLater(() -> {
                card.panel.setBackground(new Color(0xFEF3C7));
                card.carLabel.setText(carId);
                card.bayLabel.setText("Bay " + id + " - Busy");
            });
        }

        private void setFree() {
            busy = false;
            SwingUtilities.invokeLater(() -> {
                card.panel.setBackground(Color.WHITE);
                card.carLabel.setText("Free");
                card.bayLabel.setText("Bay " + id + " - Free");
                card.progress.setValue(0);
            });
        }

        @Override
        public void run() {
            while (true) {
                try { full.P(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (!running) break;

                String carId = null;
                try {
                    // remove car from queue under mutex
                    mutex.P();
                    synchronized (queue) {
                        carId = queue.poll();
                        if (carId != null) {
                            final String rem = carId;
                            SwingUtilities.invokeLater(() -> queueListModel.removeElement(rem));
                        }
                    }
                    mutex.V();
                    // free a slot in waiting area
                    empty.V();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (carId == null) continue;

                // Acquire a service bay BEFORE starting service (edge behavior fix)
                try {
                    pumpsSem.P(); // acquire bay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                // Now mark as busy and log login/begin messages
                busy = true;
                log("Pump " + id + ": " + carId + " login");
                setBusyVisual(carId);
                log("Pump " + id + ": " + carId + " begins service at Bay " + id);

                // simulate service time with progress updates
                int time = serviceMin + (serviceMax > serviceMin ? rnd.nextInt(serviceMax - serviceMin + 1) : 0);
                int steps = 50;
                int stepTime = Math.max(10, time / steps);
                for (int p = 1; p <= steps && running; p++) {
                    final int percent = (p * 100) / steps;
                    SwingUtilities.invokeLater(() -> card.progress.setValue(percent));
                    try { Thread.sleep(stepTime); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }

                log("Pump " + id + ": " + carId + " finishes service");
                // release bay
                pumpsSem.V();

                // mark free visually and logically
                setFree();
                log("Pump " + id + ": Bay " + id + " is now free");
                busy = false;
            }
            setFree();
        }
    }

    // ---------- Main GUI ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

            JFrame frame = new JFrame("Car Wash & Gas Station Simulator - Light Mode");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100,700);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout(12,12));

            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(0x0ea5e9));
            JLabel title = new JLabel("Car Wash & Gas Station Simulator", SwingConstants.CENTER);
            title.setForeground(Color.WHITE);
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            header.add(title, BorderLayout.CENTER);
            header.setPreferredSize(new Dimension(0,55));
            frame.add(header, BorderLayout.NORTH);

            // Controls
            JPanel controls = new JPanel(new GridBagLayout());
            controls.setBackground(Color.WHITE);
            controls.setBorder(new EmptyBorder(10,10,10,10));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4,8,4,8);
            c.anchor = GridBagConstraints.WEST;

            JTextField waitingField = new JTextField("5",3);
            JTextField pumpsField = new JTextField("3",3);
            JTextField carsField = new JTextField("10",4);
            JTextField intervalField = new JTextField("700",4);
            JTextField minField = new JTextField("1500",4);
            JTextField maxField = new JTextField("4000",4);

            int gx=0;
            c.gridx=gx++; c.gridy=0; controls.add(new JLabel("Waiting Cap:"),c);
            c.gridx=gx++; controls.add(waitingField,c);
            c.gridx=gx++; controls.add(new JLabel("Pumps:"),c);
            c.gridx=gx++; controls.add(pumpsField,c);
            c.gridx=gx++; controls.add(new JLabel("Cars:"),c);
            c.gridx=gx++; controls.add(carsField,c);
            c.gridx=0; c.gridy=1; controls.add(new JLabel("Arrival (ms):"),c);
            c.gridx=1; controls.add(intervalField,c);
            c.gridx=2; controls.add(new JLabel("Service Min (ms):"),c);
            c.gridx=3; controls.add(minField,c);
            c.gridx=4; controls.add(new JLabel("Service Max (ms):"),c);
            c.gridx=5; controls.add(maxField,c);

            JButton start = new JButton("Start");
            JButton stop = new JButton("Stop");

            Color grayText = new Color(0x4B5563);
            start.setBackground(new Color(0xD1FAE5));
            start.setForeground(grayText);
            stop.setBackground(new Color(0xFECACA));
            stop.setForeground(grayText);

            start.setFont(start.getFont().deriveFont(Font.BOLD, 14f));
            stop.setFont(stop.getFont().deriveFont(Font.BOLD, 14f));
            stop.setEnabled(false);

            JPanel buttons = new JPanel();
            buttons.setBackground(Color.WHITE);
            buttons.add(start);
            buttons.add(stop);

            JPanel controlBox = new JPanel(new BorderLayout());
            controlBox.add(controls, BorderLayout.CENTER);
            controlBox.add(buttons, BorderLayout.SOUTH);
            frame.add(controlBox, BorderLayout.SOUTH);

            // Left pumps area
            JPanel pumpsPanel = new JPanel();
            JScrollPane pumpsScroll = new JScrollPane(pumpsPanel);
            pumpsScroll.setBorder(BorderFactory.createTitledBorder("Pumps"));
            pumpsPanel.setBackground(Color.decode("#f9fafb"));

            // Right side
            DefaultListModel<String> waitingModel = new DefaultListModel<>();
            JList<String> waitingList = new JList<>(waitingModel);
            waitingList.setBackground(Color.WHITE);
            waitingList.setBorder(new LineBorder(new Color(0xE5E7EB)));
            JScrollPane waitingScroll = new JScrollPane(waitingList);
            waitingScroll.setBorder(BorderFactory.createTitledBorder("Waiting Area"));

            JTextArea logArea = new JTextArea();
            logArea.setBackground(Color.WHITE);
            logArea.setEditable(false);
            JScrollPane logScroll = new JScrollPane(logArea);
            logScroll.setBorder(BorderFactory.createTitledBorder("Event Log"));

            JPanel right = new JPanel(new BorderLayout(10,10));
            right.add(waitingScroll, BorderLayout.NORTH);
            right.add(logScroll, BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pumpsScroll, right);
            split.setResizeWeight(0.65);
            frame.add(split, BorderLayout.CENTER);

            ServiceStationSimulator sim = new ServiceStationSimulator(logArea, pumpsPanel, waitingList);

            start.addActionListener((ActionEvent e) -> {
                try {
                    int w = Integer.parseInt(waitingField.getText().trim());
                    int p = Integer.parseInt(pumpsField.getText().trim());
                    int cars = Integer.parseInt(carsField.getText().trim());
                    int interval = Integer.parseInt(intervalField.getText().trim());
                    int min = Integer.parseInt(minField.getText().trim());
                    int max = Integer.parseInt(maxField.getText().trim());
                    logArea.setText("");
                    sim.startSimulation(w, p, cars, interval, min, max);
                    start.setEnabled(false);
                    stop.setEnabled(true);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Please enter valid integer values.", "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            stop.addActionListener(e -> {
                sim.stopSimulation();
                start.setEnabled(true);
                stop.setEnabled(false);
            });

            frame.setVisible(true);
        });
    }
}
