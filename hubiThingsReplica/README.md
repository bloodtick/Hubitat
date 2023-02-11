<h1>HubiThings Replica</h1>

A [Hubitat application](https://community.hubitat.com/t/release-hubithings-replica/107976) to allow real-time bi-direction updates between Hubitat and SmartThings devices and modes utilizing the SmartThings OAuth Cloud API. Includes the ability to have multiple ST locations with multiple ST hubs replicated to a single Hubitat hub. It is not a requirement to have a ST hub to operate mirror functions to cloud native ST devices.

This solution requires a reasonable degree of understanding of both Hubitat and SmartThings. The original design was to mirror the few devices I have remaining in SmartThings to Hubitat in a real time fashion, but grew into a full project thanks to @Alwas, @bthrock, @hendrec, @swade, and @hendo25.

One primary use is to allow ST [webCoRE](https://community.hubitat.com/t/webcore-documentation-digest/88285) users to continue enjoying that application operating on Hubitat. Please note, this is NOT a replacement for [HubConnect](https://community.hubitat.com/t/release-hubconnect-share-devices-across-multiple-hubs-no-longer-smartthings/12028) and doesn't operate the same. <i>Obvious shout out to both incredible products</i>.

The HubiThings Replica application collects the capabilities of the ST device and stores the information in the HE device data section. Then using 'rules' to define commands and attributes from both the ST device and HE device establishes mirror functions. The HubiThings OAuth application drives real-time communication between ST and HE and issues those events to Replica. There are native Replica devices handlers and they auto-configure 'rules' for you - suggest you use them.
There are two required applications and many and growing native Replica device handlers (not required but advised).

HubiThings Replica (Install first):<br/>
Location: https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/hubiThingsReplica.groovy<br/>
Installation: Load into the 'Apps code' area of HE. You do not need to enable OAuth for this application.

HubiThings OAuth (Install second):<br/>
Location: https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/hubiThingsOauth.groovy<br/>
Installation: Load into the 'Apps code' area of HE. <b>You MUST ENABLE OAuth for this application</b>.

Replica Device Handlers:<br/>
Location: https://github.com/bloodtick/Hubitat/tree/main/hubiThingsReplica/devices<br/>
Location: https://github.com/TheMegamind/Replica-Drivers<br/>
Location: https://github.com/dds82/replica<br/>
Location: [https://github.com/DaveGut/HubitatActive/tree/master/HubiThingsReplicaDrivers](https://github.com/DaveGut/HubitatActive/tree/master/HubiThingsReplica%20Drivers)<br/>
Installation: Load into the 'Drivers code' area of HE. You can load as many, or as few as needed. The application is looking for any DH that has namespace 'replica'. If you want to design your own, please let me know and we can post locations here for others to use!

Operation:
1. Install from the "Add User App" section "HubiThings Replica".
2. Replica will prompt you to close after install and then reopen.
3. Supply a full credited [SmartThings PAT](https://account.smartthings.com/tokens) which will then allow the OAuth application to be accessed.
4. Follow the prompt and install a HubiThings OAuth (it is a child app).
5. Continue to follow the OAuth prompts and when successful you are able to pick ST devices to mirror.
6. Return back to the Replica app and you should see the device(s) in the 'Replica Device List'.
7. Click 'Create HubiThings Device' and follow the creation process. (Start easy with a simple ST device and use a Replica DH).
8. If #7 was a Replica DH, the rules and device will auto configure and you are ready, if you pick a Virtual device, you will now need to go to "Configure HubiThings Rules" and match attributes to commands.

Don't get frustrated. It works and has SUPER fast response from and to the SmartThings Cloud API. :grinning:

Note: Please be patient with questions and hopefully the team who participated in the Alpha can help you as needed. I am looking forward to what others can help bring to this project - it was designed for collaboration.

Update 2022/12/21: See this [post](https://community.hubitat.com/t/beta-hubithings-replica/107976/15) to allow for bi-directional 'mode' replication between ST and HE.<br/>
Update 2022/12/24: Beta release 1.2.05. Change log [here](https://community.hubitat.com/t/beta-hubithings-replica/107976/26).<br/>
Update 2022/01/01: Happy New Year. First OCF driver [posted](https://community.hubitat.com/t/beta-hubithings-replica/107976/57).<br/>
Update 2023/01/06: Beta release 1.2.09. Change log [here](https://community.hubitat.com/t/beta-hubithings-replica/107976/74).<br/>
Update 2023/01/07: Beta release 1.2.10. Change log [here](https://community.hubitat.com/t/beta-hubithings-replica/107976/82).<br/>
Update 2023/01/13: Release 1.3.00. Change log [here](https://community.hubitat.com/t/beta-hubithings-replica/107976/152).<br/>
Update 2023/01/17: Release 1.3.00. Added to HPM. For existing installs see [here](https://community.hubitat.com/t/release-hubithings-replica/107976/185).<br/>
Update 2023/01/29: Release 1.3.02. Change log [here](https://community.hubitat.com/t/release-hubithings-replica/107976/233).<br/>
Update 2023/02/10: Release 1.3.03. Change log [here](https://community.hubitat.com/t/release-hubithings-replica/107976/249).<br/>
