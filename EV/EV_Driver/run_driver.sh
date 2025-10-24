#!/bin/bash

# Ir al directorio EV si no estamos allí
cd "$(dirname "$0")"

echo "Compilando EV_Driver..."
javac -cp ".:kafka-clients-3.8.0.jar:jackson-core-2.15.2.jar:jackson-databind-2.15.2.jar" EV_Driver/EV_Driver.java

if [ $? -eq 0 ]; then
    echo "Compilación exitosa"
    echo ""
    echo "Ejecutando EV_Driver..."
    java -cp ".:EV_Driver:kafka-clients-3.8.0.jar:jackson-core-2.15.2.jar:jackson-databind-2.15.2.jar" EV.EV_Driver.EV_Driver localhost:9092 1 servicios.txt
else
    echo "Error en la compilación"
fi