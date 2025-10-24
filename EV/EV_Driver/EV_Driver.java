package EV.EV_Driver;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class EV_Driver {
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final String driverId;
    private final String servicesFile;
    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> users;
    private List<Map<String, Object>> chargingPoints;
    private final String basePath;
    
    // Para manejo concurrente
    private final ExecutorService executor;
    private final Map<String, String> activeSessions; // CP_ID -> Session data
    private final CountDownLatch completionLatch;

    public EV_Driver(Properties producerProps, Properties consumerProps, String driverId, String servicesFile) {
        this.producer = new KafkaProducer<>(producerProps);
        this.consumer = new KafkaConsumer<>(consumerProps);
        this.driverId = driverId;
        this.servicesFile = servicesFile;
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool();
        this.activeSessions = new ConcurrentHashMap<>();
        this.completionLatch = new CountDownLatch(1);
        
        this.basePath = determineBasePath();
        
        System.out.println("Buscando archivos JSON en: " + basePath);
        
        this.users = loadUsers();
        this.chargingPoints = loadChargingPoints();
        validateDriver();
        
        this.consumer.subscribe(Arrays.asList(
            "charging-authorizations", 
            "charging-session-data", 
            "session-end",
            "cp-status"
        ));
    }

    private String determineBasePath() {
        // Intentar diferentes estrategias para encontrar la ruta base
        try {
            // Estrategia 1: Desde el classpath (cuando se ejecuta desde EV/)
            File currentDir = new File(".");
            String currentPath = currentDir.getAbsolutePath();
            System.out.println("Directorio actual: " + currentPath);
            
            // Si estamos en EV/EV_Driver, subir un nivel
            if (currentPath.contains("EV_Driver")) {
                return new File("..").getCanonicalPath();
            }
            
            // Estrategia 2: Buscar la carpeta CL
            File clDir = new File("CL");
            if (clDir.exists()) {
                return ".";
            }
            
            // Estrategia 3: Buscar desde el directorio padre
            clDir = new File("../CL");
            if (clDir.exists()) {
                return "..";
            }
            
            // Estrategia 4: Usar ruta absoluta basada en la ubicación de la clase
            String classLocation = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            File classDir = new File(classLocation);
            // Navegar hasta EV/ desde EV/EV_Driver/
            File evDir = classDir.getParentFile().getParentFile();
            if (new File(evDir, "CL").exists()) {
                return evDir.getAbsolutePath();
            }
            
        } catch (Exception e) {
            System.err.println("Error determinando ruta base: " + e.getMessage());
        }
        
        return "."; // Fallback al directorio actual
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Uso: EV_Driver <bootstrap_servers> <driver_id> <services_file>");
            System.err.println("Ejemplo: EV_Driver localhost:9092 1 servicios.txt");
            System.err.println("IDs de driver disponibles: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10");
            System.exit(1);
        }

        String bootstrapServers = args[0];
        String driverId = args[1];
        String servicesFile = args[2];

        // Configuración del productor
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // Configuración del consumidor
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", bootstrapServers);
        consumerProps.put("group.id", "EV_Driver_" + driverId);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("auto.offset.reset", "earliest");

        EV_Driver driver = new EV_Driver(producerProps, consumerProps, driverId, servicesFile);
        driver.start();
    }

    private List<Map<String, Object>> loadUsers() {
        try {
            // Buscar users.json en diferentes ubicaciones
            File[] possiblePaths = {
                new File(basePath + "/CL/users.json"),
                new File(basePath + "/users.json"),
                new File("../CL/users.json"),
                new File("CL/users.json"),
                new File("users.json")
            };
            
            for (File file : possiblePaths) {
                if (file.exists()) {
                    System.out.println("Cargando usuarios desde: " + file.getAbsolutePath());
                    return objectMapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
                }
            }
            
            System.err.println("No se pudo encontrar users.json en las siguientes ubicaciones:");
            for (File file : possiblePaths) {
                System.err.println("  - " + file.getAbsolutePath());
            }
            
        } catch (Exception e) {
            System.err.println("Error cargando usuarios: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private List<Map<String, Object>> loadChargingPoints() {
        try {
            // Buscar charging_points.json en diferentes ubicaciones
            File[] possiblePaths = {
                new File(basePath + "/CL/charging_points.json"),
                new File(basePath + "/charging_points.json"),
                new File("../CL/charging_points.json"),
                new File("CL/charging_points.json"),
                new File("charging_points.json")
            };
            
            for (File file : possiblePaths) {
                if (file.exists()) {
                    System.out.println("Cargando puntos de carga desde: " + file.getAbsolutePath());
                    return objectMapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
                }
            }
            
            System.err.println("No se pudo encontrar charging_points.json en las siguientes ubicaciones:");
            for (File file : possiblePaths) {
                System.err.println("  - " + file.getAbsolutePath());
            }
            
        } catch (Exception e) {
            System.err.println("Error cargando puntos de carga: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private void validateDriver() {
        if (users.isEmpty()) {
            System.err.println("ERROR: No se pudieron cargar los usuarios");
            System.exit(1);
        }
        
        boolean driverExists = users.stream()
            .anyMatch(user -> driverId.equals(user.get("id").toString()));
        
        if (!driverExists) {
            System.err.println("ERROR: Driver ID " + driverId + " no encontrado en el sistema");
            System.err.println("IDs válidos: " + 
                users.stream().map(u -> u.get("id").toString()).reduce((a, b) -> a + ", " + b).orElse(""));
            System.exit(1);
        }
        
        // Mostrar información del driver
        users.stream()
            .filter(user -> driverId.equals(user.get("id").toString()))
            .findFirst()
            .ifPresent(user -> {
                System.out.println("🚗 Conductor: " + user.get("name") + " (ID: " + user.get("id") + ")");
                System.out.println("📧 Email: " + user.get("gmail"));
            });
    }

    public void start() {
        System.out.println("\n=== EV_Driver " + driverId + " iniciado ===");
        System.out.println("Leyendo servicios del archivo: " + servicesFile);
        
        // Mostrar estado actual de los puntos de carga
        showAvailableChargingPoints();

        // Leer servicios del archivo
        List<String> chargingPointsToUse = readServicesFile();
        if (chargingPointsToUse.isEmpty()) {
            System.out.println("No se encontraron servicios en el archivo.");
            return;
        }

        // Iniciar consumidor en hilo separado
        executor.submit(this::consumeMessages);

        // Procesar servicios CONCURRENTEMENTE
        processServicesConcurrently(chargingPointsToUse);

        // Esperar a que todos los servicios terminen
        try {
            completionLatch.await(5, TimeUnit.MINUTES); // Timeout de 5 minutos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n=== Todos los servicios procesados ===");
        shutdown();
    }

    private void processServicesConcurrently(List<String> chargingPointsToUse) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < chargingPointsToUse.size(); i++) {
            final String cpId = chargingPointsToUse.get(i).trim();
            final int delaySeconds = i * 4; // Espaciar solicitudes cada 4 segundos
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Esperar tiempo escalonado para evitar saturación
                    if (delaySeconds > 0) {
                        Thread.sleep(delaySeconds * 1000);
                    }
                    
                    processChargingService(cpId);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Error procesando servicio para CP " + cpId + ": " + e.getMessage());
                }
            }, executor);
            
            futures.add(future);
        }

        // Esperar a que todas las solicitudes se completen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> completionLatch.countDown());
    }

    private void showAvailableChargingPoints() {
        if (chargingPoints.isEmpty()) {
            System.out.println("No se pudieron cargar los puntos de carga");
            return;
        }
        
        System.out.println("\n--- PUNTOS DE CARGA DISPONIBLES ---");
        chargingPoints.forEach(cp -> {
            String id = cp.get("id").toString();
            String state = cp.get("state").toString();
            double price = Double.parseDouble(cp.get("priceEurKwh").toString());
            double posX = Double.parseDouble(cp.get("posX").toString());
            double posY = Double.parseDouble(cp.get("posY").toString());
            
            String statusIcon = getStatusIcon(state);
            String statusText = getStatusText(state);
            
            System.out.printf("%s CP%s | €%.2f/kWh | Posición: (%.1f, %.1f) | %s%n",
                statusIcon, id, price, posX, posY, statusText);
        });
        System.out.println("-----------------------------------\n");
    }

    private String getStatusIcon(String state) {
        switch (state) {
            case "AVAILABLE": return "🟢";
            case "CHARGING": return "🔵";
            case "BROKEN": return "🔴";
            case "DISCONNECTED": return "⚫";
            case "OUT_OF_SERVICE": return "🟠";
            default: return "⚪";
        }
    }

    private String getStatusText(String state) {
        switch (state) {
            case "AVAILABLE": return "Disponible";
            case "CHARGING": return "Cargando";
            case "BROKEN": return "Averiado";
            case "DISCONNECTED": return "Desconectado";
            case "OUT_OF_SERVICE": return "Fuera de servicio";
            default: return "Desconocido";
        }
    }

    private List<String> readServicesFile() {
        List<String> chargingPoints = new ArrayList<>();
        try {
            // Buscar el archivo de servicios
            File services = new File(servicesFile);
            if (!services.exists()) {
                // Intentar con rutas relativas
                services = new File(basePath + "/" + servicesFile);
                if (!services.exists()) {
                    System.err.println("Archivo de servicios no encontrado: " + servicesFile);
                    return chargingPoints;
                }
            }
            
            chargingPoints = Files.readAllLines(services.toPath());
            System.out.println("Servicios a procesar: " + chargingPoints);
            
            // Validar que los CPs existen
            if (!this.chargingPoints.isEmpty()) {
                chargingPoints.forEach(cpId -> {
                    boolean cpExists = this.chargingPoints.stream()
                        .anyMatch(cp -> cpId.trim().equals(cp.get("id").toString()));
                    if (!cpExists) {
                        System.err.println("ADVERTENCIA: CP " + cpId + " no encontrado en el sistema");
                    }
                });
            }
            
        } catch (IOException e) {
            System.err.println("Error leyendo archivo de servicios: " + e.getMessage());
        }
        return chargingPoints;
    }

    private void processChargingService(String cpId) {
        System.out.println("\n[" + driverId + "] --- Solicitando carga en CP: " + cpId + " ---");
        
        // Verificar estado del CP localmente (validación adicional)
        if (!chargingPoints.isEmpty()) {
            chargingPoints.stream()
                .filter(cp -> cpId.equals(cp.get("id").toString()))
                .findFirst()
                .ifPresent(cp -> {
                    String state = cp.get("state").toString();
                    if (!"AVAILABLE".equals(state)) {
                        System.out.println("[" + driverId + "] ⚠️  ADVERTENCIA: CP " + cpId + " está en estado: " + getStatusText(state));
                    }
                });
        }

        // Enviar solicitud de carga
        String requestMessage = String.format("%s|%s|REQUEST|%s", 
            driverId, cpId, System.currentTimeMillis());
        
        ProducerRecord<String, String> record = new ProducerRecord<>(
            "charging-requests", 
            driverId + "_" + cpId, // Key única por conductor+CP
            requestMessage
        );

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("[" + driverId + "] Error enviando solicitud de carga: " + exception.getMessage());
            } else {
                System.out.println("[" + driverId + "] ✅ Solicitud de carga enviada para CP: " + cpId);
                System.out.println("[" + driverId + "]    Esperando autorización de la central...");
            }
        });
        producer.flush();
    }

    private void consumeMessages() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    // Procesar cada mensaje en un hilo separado para mayor concurrencia
                    executor.submit(() -> processMessage(record));
                }
            }
        } catch (Exception e) {
            if (!(e.getCause() instanceof WakeupException)) {
                System.err.println("[" + driverId + "] Error en consumidor: " + e.getMessage());
            }
        } finally {
            consumer.close();
        }
    }

    private void processMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String key = record.key();
        String value = record.value();
        
        System.out.printf("\n[%s][Mensaje] Topic: %s | Key: %s | Value: %s%n", 
                         driverId, topic, key, value);

        // Filtrar mensajes que son para este conductor específico
        if (key != null && key.startsWith(driverId + "_")) {
            String cpId = key.replace(driverId + "_", "");
            switch (topic) {
                case "charging-authorizations":
                    handleAuthorizationMessage(cpId, value);
                    break;
                case "charging-session-data":
                    handleSessionData(cpId, value);
                    break;
                case "session-end":
                    handleSessionEnd(cpId, value);
                    break;
            }
        }
        
        // Los mensajes de estado de CP son para todos los conductores
        if ("cp-status".equals(topic)) {
            handleCpStatus(key, value);
        }
    }

    private void handleAuthorizationMessage(String cpId, String message) {
        System.out.println("\n[" + driverId + "] --- AUTORIZACIÓN RECIBIDA ---");
        System.out.println("[" + driverId + "] CP: " + cpId + " | Mensaje: " + message);
        
        if (message.contains("AUTHORIZED") || message.contains("autorizado") || message.contains("APPROVED")) {
            System.out.println("[" + driverId + "] ✅ ✅ ✅ CARGA AUTORIZADA ✅ ✅ ✅");
            System.out.println("[" + driverId + "] Proceder a conectar vehículo al CP " + cpId);
            
            // Marcar sesión como activa
            activeSessions.put(cpId, "AUTHORIZED");
        } else if (message.contains("DENIED") || message.contains("denegado") || message.contains("REJECTED")) {
            System.out.println("[" + driverId + "] ❌ ❌ ❌ CARGA DENEGADA ❌ ❌ ❌");
            System.out.println("[" + driverId + "] Motivo: " + message);
            
            // Remover sesión denegada
            activeSessions.remove(cpId);
        } else {
            System.out.println("[" + driverId + "] 📋 Estado de autorización: " + message);
        }
    }

    private void handleSessionData(String cpId, String data) {
        System.out.println("[" + driverId + "] 📊 [CP " + cpId + "] Datos de carga: " + data);
        
        // Parsear datos de la sesión
        String[] parts = data.split("\\|");
        if (parts.length >= 4) {
            try {
                double power = Double.parseDouble(parts[1]);
                double energy = Double.parseDouble(parts[2]);
                double cost = Double.parseDouble(parts[3]);
                
                System.out.printf("[%s]    ⚡ Potencia: %.1f kW | 🔋 Energía: %.1f kWh | 💰 Coste: %.2f €%n", 
                    driverId, power, energy, cost);
            } catch (NumberFormatException e) {
                System.out.println("[" + driverId + "]    " + data);
            }
        }
    }

    private void handleSessionEnd(String cpId, String message) {
        System.out.println("\n[" + driverId + "] --- FIN DE SESIÓN ---");
        System.out.println("[" + driverId + "] CP: " + cpId);
        System.out.println("[" + driverId + "] 🎫 Ticket final: " + message);
        
        // Remover sesión terminada
        activeSessions.remove(cpId);
        
        if (message.contains("|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 4) {
                System.out.printf("[%s]    🔋 Energía total: %s kWh%n", driverId, parts[1]);
                System.out.printf("[%s]    💰 Coste total: %s €%n", driverId, parts[2]);
                System.out.printf("[%s]    ⏱️  Tiempo de carga: %s%n", driverId, parts[3]);
            }
        }
    }

    private void handleCpStatus(String cpId, String status) {
        System.out.println("[" + driverId + "] 🔧 [CP " + cpId + "] Estado actualizado: " + status);
    }

    private void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        producer.close();
        consumer.wakeup();
    }
}
