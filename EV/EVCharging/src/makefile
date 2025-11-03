JARS = EV/gson-2.10.1.jar:EV/kafka-clients-3.6.0.jar:EV/slf4j-api-2.0.9.jar:EV/slf4j-simple-2.0.9.jar

clean:
	find . -name "*.class" -type f -delete

compile:
	javac -cp .:$(JARS) EV/*.java

all: clean compile

.PHONY: clean compile all