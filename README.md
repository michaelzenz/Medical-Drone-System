# Medical-Drone-System
A medical drone system project for UCI EECS 159: Senior Design

This project aims to build a medical drone system, which allows its users to acquire medical support delivered by an AI-Controlled drone under emergencies to grand expensive devices like AED a wider accessibility

![](./res/Slides.jpg)

The uesr can request for medical support through the UserApp, then a drone with the ordered medicine or medical device will be automatically delivered to the user. The drone will try to identify the user through computer vision and try to perform auto-landing when reaches the user.

# Project Structure
|Branch|Description|
|---|---|
|master|description about the project|
|ControlApp|The app that only implements the controller of the drone|
|Document|Some course materials|
|Server|The server program|
|UserApp|The app for users to request a medical support|
|Vision|The app that implements both controller and computer vision part|