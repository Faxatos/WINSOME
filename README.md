# WINSOME

Client-server application inspired from [steemit](https://steemit.com/)

Network Laboratory Course Project - Final Grade: 30/30

## System Architecture Overview

The system architecture adopts a *thin client model*, where the server hosts the majority of functionalities. This document outlines the specific functionalities of each artifact within the system.

### Server

The server software loads and creates social network-related data upon startup, exporting the RMI interface-bound object for client discovery. It also initiates periodic multicast communication to notify clients of reward calculations.

##### Functionality Highlights:
- *Social network functionalities*: Implements social network functionalities that [can be utilized from client](#commands).
- *Command-line interface*: Used to monitor clients interactions and to stop the server if needed.
- *Data Management*: Loads and creates social network-related data upon startup. Updates data storages during the server shutdown phase.
- *RMI Interface Export*: Exposes an RMI interface-bound object for client sign-up and multicast registration.
- *Multicast Communication*: Periodically notifies clients of reward calculations via multicast communication.

Following this, a thread pool is opened, with the selector assigning various tasks related to client requests for execution. Due to potential simultaneous task execution by multiple threads, synchronization is necessary for accessing certain data structures.

Upon server shutdown (triggered by the command *exit*), the social network state is preserved by storing users and posts in separate JSON files (*postsDB.json* and *usersDB.json*), facilitating retrieval upon system restart.

### Client

The client software initiates communication with the server upon startup using TCP. Additionally, it can communicate with the server via an RMI interface for certain operations such as registration, subscription to notification services, and unsubscription from the same.

##### Functionality Highlights:
- *Command-line interface*: Users can utilize the CLI to interact with the server functionalities.
- *TCP Communication*: Establishes initial communication with the server using sockets.
- *RMI Interface Export*: Exposes an RMI interface-bound object to allow the server to update the locally stored list of followers.
- *Notification Service*: Allows users to subscribe and unsubscribe from notification services, implicitly handled during the login phase.

Upon successful login, the client receives data necessary for connecting to the relevant Multicast group to receive server notifications regarding reward updates within the system. The client also exposes its RMI interface to the server, enabling the server to maintain an updated local copy of followers associated with the user who published the object.

Throughout the user's session within the system, communication occurs in a question/answer format. Despite being a thin client system, controls are implemented to prevent the server from handling requests that will ultimately be discarded.

## <a name="commands"></a> Client Commands:

Commands that can only be performed when *not logged* in:

- **register \<username\> \<password> \<tags>**: Registers a new user. The server provides an operation to register a user where the user must provide a username, password, and a list of tags (up to 5 tags).
- **login \<username> \<password>**: Logs in a registered user to access the service.

Commands that can only be performed when *logged* in:

- **list users**: Initializes a page containing users of the platform who have tags in common with you.
- **list following**: Initializes a page containing users of the platform whom you are following.
- **list followers**: Initializes a page containing users of the platform who are following you.
- **follow \<username>**: Allows you to follow the user identified by *\<username>*.
- **unfollow \<username>**: Allows you to stop following the user identified by *\<username>*.
- **blog**: Initializes a page containing posts published by you (including rewins).
- **post <"*title*"> <"*content*">**: Allows you to publish a post with the title "*title*" and content "*content*". Using the special character | may lead to anomalies in post creation.
- **show feed**: Initializes a page containing posts published by users you follow. The command searches up to n days backward from the last post viewed in the feed.
- **show post \<post id>**: Displays the post (if contained in your feed) identified by *\<post id>*, and then initializes a page containing the comments on the post.
- **rewin \<post id>**: Allows the reposting (rewin) of a post (if contained in your feed) identified by *\<post id>*.
- **rate \<post id> \<rating>**: Allows you to rate a post identified by *\<post id>* (if contained in your feed). Once rated, you cannot change your rating.
- **comment \<post id> \<"*comment*">**: Allows you to comment on a post identified by *\<post id>* (if contained in your feed).
- **delete \<post id>**: Allows you to delete a post identified by *\<post id>* (only if you are the creator).
- **wallet**: Prints the total wincoins in your possession, and then initializes a page containing the list of transactions.
- **wallet btc**: Shows how much our wincoins are worth when converted into bitcoins.
- **logout \<username>**: Logs the user out of the service.

## License

Distributed under the MIT License. See `LICENSE.txt` for more information.
