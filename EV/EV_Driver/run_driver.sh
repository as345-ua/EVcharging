#!/bin/bash

# Ir al directorio EV si no estamos allí
cd "$(dirname "$0")"

# Configuración
DRIVER_ID="${1:-1}"  # Usar parámetro o default 1
SERVICES_FILE="${2:-servicios.txt}"  # Usar parámetro o default servicios.txt
KAFKA_SERVER="${3:-localhost:9092}"  # Usar parámetro o default localhost:9092

# Verificar que existen los archivos JAR necesarios
check_jar_files() {
    local missing_jars=()
    
    [ ! -f "kafka-clients-3.8.0.jar" ] && missing_jars+=("kafka-clients-3.8.0.jar")
    [ ! -f "jackson-core-2.15.2.jar" ] && missing_jars+=("jackson-core-2.15.2.jar") 
    [ ! -f "jackson-databind-2.15.2.jar" ] && missing_jars+=("jackson-databind-2.15.2.jar")
    
    if [ ${#missing_jars[@]} -ne 0 ]; then
        echo "ERROR: Faltan los siguientes archivos JAR:"
        for jar in "${missing_jars[@]}"; do
            echo "  - $jar"
        done
        echo ""
        echo "Descarga los JARs necesarios o ajusta las versiones en el script."
        exit 1
    fi
}

# Verificar que existe el archivo de servicios
check_services_file() {
    if [ ! -f "$SERVICES_FILE" ]; then
        echo "ERROR: Archivo de servicios no encontrado: $SERVICES_FILE"
        echo ""
        echo "Crea el archivo $SERVICES_FILE con el formato:"
        echo "1"
        echo "6" 
        echo "2"
        echo "5"
        exit 1
    fi
}

# Mostrar información de configuración
show_config() {
    echo "=== Configuración EV_Driver ==="
    echo "Driver ID: $DRIVER_ID"
    echo "Servicios: $SERVICES_FILE"
    echo "Kafka: $KAFKA_SERVER"
    echo "================================"
    echo ""
}

# Verificaciones iniciales
check_jar_files
check_services_file
show_config

echo "Compilando EV_Driver..."
javac -cp ".:kafka-clients-3.8.0.jar:jackson-core-2.15.2.jar:jackson-databind-2.15.2.jar" EV_Driver/EV_Driver.java

if [ $? -eq 0 ]; then
    echo "✅ Compilación exitosa"
    echo ""
    echo "🚀 Ejecutando EV_Driver..."
    echo "   Driver: $DRIVER_ID"
    echo "   Servicios: $SERVICES_FILE" 
    echo "   Kafka: $KAFKA_SERVER"
    echo ""
    
    java -cp ".:EV_Driver:kafka-clients-3.8.0.jar:jackson-core-2.15.2.jar:jackson-databind-2.15.2.jar" \
         EV.EV_Driver.EV_Driver "$KAFKA_SERVER" "$DRIVER_ID" "$SERVICES_FILE"
else
    echo "❌ Error en la compilación"
    exit 1
fi