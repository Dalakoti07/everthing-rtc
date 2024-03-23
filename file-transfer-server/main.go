package main

/*
import (
    "log"
    "net/http"

    "github.com/gorilla/websocket"
)

// User represents a user with their name and connection object
type User struct {
    Name string
    Conn *websocket.Conn
}

var users []User

func main() {
    http.HandleFunc("/", handleConnections)
    log.Fatal(http.ListenAndServe(":3000", nil))
}

func handleConnections(w http.ResponseWriter, r *http.Request) {
    upgrader := websocket.Upgrader{
        ReadBufferSize:  1024,
        WriteBufferSize: 1024,
    }

    conn, err := upgrader.Upgrade(w, r, nil)
    if err != nil {
        log.Println("Error upgrading to WebSocket:", err)
        return
    }
    defer conn.Close()

    for {
        var message map[string]interface{}
        err := conn.ReadJSON(&message)
        if err != nil {
            log.Println("Error reading JSON:", err)
            break
        }

        name := message["name"].(string)
        user := findUser(name)

        switch message["type"].(string) {
        case "store_user":
            if user != nil {
                response := map[string]interface{}{
                    "type": "user_already_exists",
                    "data": name,
                }
                if err := conn.WriteJSON(response); err != nil {
                    log.Println("Error sending user already exists response:", err)
                }
                log.Println("User already exists, returning")
            }
            newUser := User{Name: name, Conn: conn}
            users = append(users, newUser)
            response := map[string]interface{}{
                "type": "user_stored",
            }
            if err := conn.WriteJSON(response); err != nil {
                log.Println("Error sending user stored response:", err)
            }
            log.Println("User added")
        case "start_transfer":
            userToCall := findUser(message["target"].(string))
            if userToCall != nil {
                response := map[string]interface{}{
                    "type": "transfer_response",
                    "data": "user is ready for call",
                }
                if err := conn.WriteJSON(response); err != nil {
                    log.Println("Error sending transfer response:", err)
                }
            } else {
                response := map[string]interface{}{
                    "type": "transfer_response",
                    "data": "user is not online",
                }
                if err := conn.WriteJSON(response); err != nil {
                    log.Println("Error sending transfer response:", err)
                }
            }
        case "create_offer", "create_answer", "ice_candidate":
            target := findUser(message["target"].(string))
            if target != nil {
                if err := target.Conn.WriteJSON(message); err != nil {
                    log.Println("Error sending message to target:", err)
                }
            }
        }
    }
}

func findUser(username string) *User {
    for i := range users {
        if users[i].Name == username {
            return &users[i]
        }
    }
    return nil
}

*/