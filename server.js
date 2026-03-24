// Ultra-lightweight Group Calling Signaling Server
// Minimal dependencies: express + socket.io

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
});

const PORT = process.env.PORT || 3000;

// Store active rooms and users
const rooms = new Map(); // roomId -> Set of userIds
const userSockets = new Map(); // userId -> socketId

io.on('connection', (socket) => {
  console.log(`User connected: ${socket.id}`);

  // Join a room
  socket.on('join-room', ({ roomId, userId }) => {
    socket.join(roomId);
    
    if (!rooms.has(roomId)) {
      rooms.set(roomId, new Set());
    }
    rooms.get(roomId).add(userId);
    userSockets.set(userId, socket.id);
    
    // Notify others in room
    socket.to(roomId).emit('user-joined', { userId, socketId: socket.id });
    
    // Send existing users to new user
    const existingUsers = Array.from(rooms.get(roomId)).filter(id => id !== userId);
    socket.emit('room-users', { users: existingUsers });
    
    console.log(`User ${userId} joined room ${roomId}`);
  });

  // WebRTC signaling: Offer
  socket.on('offer', ({ to, from, offer }) => {
    const targetSocketId = userSockets.get(to);
    if (targetSocketId) {
      io.to(targetSocketId).emit('offer', { from, offer });
    }
  });

  // WebRTC signaling: Answer
  socket.on('answer', ({ to, from, answer }) => {
    const targetSocketId = userSockets.get(to);
    if (targetSocketId) {
      io.to(targetSocketId).emit('answer', { from, answer });
    }
  });

  // WebRTC signaling: ICE Candidate
  socket.on('ice-candidate', ({ to, from, candidate }) => {
    const targetSocketId = userSockets.get(to);
    if (targetSocketId) {
      io.to(targetSocketId).emit('ice-candidate', { from, candidate });
    }
  });

  // User left
  socket.on('leave-room', ({ roomId, userId }) => {
    socket.leave(roomId);
    if (rooms.has(roomId)) {
      rooms.get(roomId).delete(userId);
      if (rooms.get(roomId).size === 0) {
        rooms.delete(roomId);
      }
    }
    userSockets.delete(userId);
    socket.to(roomId).emit('user-left', { userId });
    console.log(`User ${userId} left room ${roomId}`);
  });

  // Disconnect
  socket.on('disconnect', () => {
    console.log(`User disconnected: ${socket.id}`);
    
    // Clean up from all rooms
    for (const [roomId, users] of rooms.entries()) {
      for (const userId of users) {
        if (userSockets.get(userId) === socket.id) {
          users.delete(userId);
          io.to(roomId).emit('user-left', { userId });
          break;
        }
      }
      if (users.size === 0) {
        rooms.delete(roomId);
      }
    }
    
    for (const [userId, socketId] of userSockets.entries()) {
      if (socketId === socket.id) {
        userSockets.delete(userId);
        break;
      }
    }
  });
});

server.listen(PORT, () => {
  console.log(`🚀 Signaling server running on port ${PORT}`);
});
