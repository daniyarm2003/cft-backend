# CFT Backend

This is the backend of the CFT, accompanying the frontend [in this repository](https://github.com/daniyarm2003/cft-react). Written in Java with the help of Spring, the backend performs simple CRUD operations on request of the frontend, 
interacting with a PostgreSQL database to store data. It also interacts with another server that provides AI predictions, uses Google's Drive and Sheets APIs to create and edit spreadsheets to save snapshots of the fighter position chart, 
and generates images on the fly that show further statistics. 
