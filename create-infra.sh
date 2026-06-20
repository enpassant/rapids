#!bash

mkdir -p ~/mongodata
mkdir -p ~/kafkadata

# 1. IP cím kinyerése az eth1 interfészről
echo "-> IP cím lekérdezése az eth1 interfészről..."
export MY_HOST_IP=$(ip -4 addr show eno1 | grep -o 'inet\s\+[0-9.]\+' | cut -d ' ' -f 2)

if [ -z "$MY_HOST_IP" ]; then
    echo "HIBA: Nem sikerült kinyerni az IP címet az eth1-ről! Helyette a localhost-ot használom."
    export MY_HOST_IP="0.0.0.0"
else
    echo "Siker: A kinyert IP cím: $MY_HOST_IP"
fi

# 2. Régi adatok kezelése (Opcionális figyelmeztetés)
# Mivel a KRaft nem kompatibilis a régi ZK adatokkal, ha tiszta indítást szeretnél,
# törölheted a ~/kafkadata tartalmát a következő sor engedélyezésével:
# rm -rf ~/kafkadata/*

# 3. Podman konténer indítása a háttérben
echo "-> Kafka indítása Podman-nel..."
podman compose -f infra-dc.yml --project-name rapids up -d

# 4. Várakozás, amíg a Kafka teljesen elindul és fogadja a parancsokat
echo "-> Várakozás a Kafka elindulására..."
# A --workdir paraméterrel pontosan a szkriptek mappájába lépünk be, és a .sh végződést használjuk
until podman exec --workdir /opt/kafka/bin/ kafka-kraft ./kafka-broker-api-versions.sh --bootstrap-server 127.0.0.1:9092 > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo -e "\n-> Kafka sikeresen elindult!"

# 5. A létrehozandó topicok listája
TOPICS=(
    "blog-command"
    "client-commands"
    "blog-event"
    "performance"
    "discussion-event"
    "web-app"
    "user"
    "discussion-command"
    "error"
)

# 6. Topicok automatikus létrehozása loop segítségével
echo "-> Topicok létrehozása..."
for TOPIC in "${TOPICS[@]}"; do
    # Ellenőrzés a megfelelő mappából indítva
    podman exec --workdir /opt/kafka/bin/ kafka-kraft ./kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list | grep -q "^${TOPIC}$"

    if [ $? -eq 0 ]; then
        echo "   [!] A topic már létezik: $TOPIC"
    else
        # Létrehozás a megfelelő mappából indítva
        podman exec --workdir /opt/kafka/bin/ kafka-kraft ./kafka-topics.sh --create \
            --bootstrap-server 127.0.0.1:9092 \
            --topic "$TOPIC" \
            --partitions 1 \
            --replication-factor 1
        echo "   [✓] Sikeresen létrehozva: $TOPIC"
    fi
done

echo "=== A Kafka KRaft fürt üzemkész és a topicok konfigurálva lettek! ==="
