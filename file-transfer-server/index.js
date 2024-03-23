const { log } = require("console")
const http = require("http")
const Socket = require("websocket").server
const server = http.createServer(()=>{})

server.listen(3000,()=>{
    log("listening on server 3000")
})

const webSocket = new Socket({httpServer:server})

// array contains user name and there connection objects
const users = []

webSocket.on('request',(req)=>{
    const connection = req.accept()

    connection.on('message',(message)=>{
        const data = JSON.parse(message.utf8Data)
        console.log(data);
        const user = findUser(data.name)
       
        switch(data.type){
            case "store_user":
                if(user !=null){
                    //our user exists
                    connection.send(JSON.stringify({
                        type:'user_already_exists',
                        data: data.name
                    }))
                    log("user already existed returning");
                    return
                }

                const newUser = {
                    name:data.name, conn: connection
                }
                try {
                    connection.send(JSON.stringify({
                        type:'user_stored',
                        data: data.name,
                    }))
                    log("user added");
                } catch (error) {
                    log("error adding -> ", error)
                }
                users.push(newUser)
            break

            case "start_transfer":
                let userToCall = findUser(data.target);

                if(userToCall){
                    connection.send(JSON.stringify({
                        type:"transfer_response", data:userToCall.name
                    }))
                } else{
                    connection.send(JSON.stringify({
                        type:"transfer_response", data: null
                    }))
                }

            break
            
            case "create_offer":
                let userToReceiveOffer = findUser(data.target)

                if (userToReceiveOffer){
                    userToReceiveOffer.conn.send(JSON.stringify({
                        type:"offer_received",
                        name:data.name,
                        data:data.data.sdp
                    }))
                }
            break
                
            case "create_answer":
                let userToReceiveAnswer = findUser(data.target)
                if(userToReceiveAnswer){
                    userToReceiveAnswer.conn.send(JSON.stringify({
                        type:"answer_received",
                        name: data.name,
                        data:data.data.sdp
                    }))
                }
            break

            case "ice_candidate":
                let userToReceiveIceCandidate = findUser(data.target)
                if(userToReceiveIceCandidate){
                    userToReceiveIceCandidate.conn.send(JSON.stringify({
                        type:"ice_candidate",
                        name:data.name,
                        data:{
                            sdpMLineIndex:data.data.sdpMLineIndex,
                            sdpMid:data.data.sdpMid,
                            sdpCandidate: data.data.sdpCandidate
                        }
                    }))
                }
            break


        }

    })
    
    connection.on('close', () =>{
        users.forEach( user => {
            if(user.conn === connection){
                users.splice(users.indexOf(user),1)
            }
        })
    })

    connection.on('error', (err)=>{
        log("error on socket, ", err)
    })

})

const findUser = username =>{
    for(let i=0; i<users.length;i++){
        if(users[i].name === username)
        return users[i]
    }
}