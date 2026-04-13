#!/bin/bash

SERVER_HOST=${SERVER_HOST:-$DEFAULT_HOST}
SERVER_PORT=${SERVER_PORT:-$DEFAULT_PORT}

check_dependencies() {
    if ! command -v $PYTHON_CMD &> /dev/null; then
        exit 1
    fi
    
    if ! command -v pip3 &> /dev/null; then
        sudo apt update && sudo apt install python3-pip -y 2>/dev/null || sudo yum install python3-pip -y 2>/dev/null
    fi
    
    if [ -f "requirements.txt" ]; then
        pip3 install -r requirements.txt --user --quiet
    fi
}

check_firewall() {
    if command -v ufw &> /dev/null; then
        UFW_STATUS=$(sudo ufw status 2>/dev/null | grep "Status:" | awk '{print $2}')
        if [ "$UFW_STATUS" = "active" ]; then
            UFW_RULE=$(sudo ufw status | grep "$SERVER_PORT" 2>/dev/null)
            if [ -z "$UFW_RULE" ]; then
                read -p "¿Quieres abrir el puerto automáticamente? (s/N): " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Ss]$ ]]; then
                    sudo ufw allow $SERVER_PORT
                    sudo ufw reload
                    echo -e "${GREEN} Puerto abierto en UFW${NC}"
                fi
            fi
        fi
    fi
    
    if command -v firewall-cmd &> /dev/null; then
        if systemctl is-active --quiet firewalld; then
            if ! sudo firewall-cmd --query-port=$SERVER_PORT/tcp --quiet 2>/dev/null; then
                read -p "¿Quieres abrir el puerto automáticamente? (s/N): " -n 1 -r
                if [[ $REPLY =~ ^[Ss]$ ]]; then
                    sudo firewall-cmd --permanent --add-port=$SERVER_PORT/tcp
                    sudo firewall-cmd --reload
                fi
            fi
        fi
    fi
}

check_server_file() {
    if [ ! -f "start_server.py" ]; then
        exit 1
    fi
}

main() {
    check_server_file
    check_dependencies
    check_firewall

    echo -e "${GREEN} Iniciando servidor...${NC}"
    echo -e ""
    
    $PYTHON_CMD start_server.py --host "$SERVER_HOST" --port "$SERVER_PORT"
    echo -e ""
    read
}

cleanup() {
    echo -e ""
    exit 0
}

trap cleanup SIGINT SIGTERM

if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi