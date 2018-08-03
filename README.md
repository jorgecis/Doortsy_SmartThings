# SmartThings code to use ESP#@_Relay [Dooortsy]
---

## Instalation 


### Using Git Integration
Enabling the GitHub Integration in the IDE is by far the easiest way to install and get the latest updates for Doortsy SmartThings smart app and the device handler.

To enable Git integration (one time configuration) under the IDE please visit here for instructions: IDE GitHub Integration Instructions

**NOTE:** Git Integration is not currently available outside of US and UK


#### Installing The Doortsy Manager and the Doortsy control.
First, find the Settings button at the top of SmartThings IDE page (this will only appear after completing the one time integration with GitHub)

![github settings](/readme_imgs/ide_settings.jpg?raw=true "GitHub settings")

Clicking this button will open the GitHub Repository Integration page.
To find the Doortsy SmartThing  code, enter the information below:

**Owner:** jorgecis

**Name:** Doortsy_SmartThings

**Branch:** master

![github params](/readme_imgs/gitss.png?raw=true "GitHub params")

Close the GitHub Repository Integration page
Next, click the Update from Repo button at the upper-right corner of the IDE
Click on Doortsy_SmartThings (master) from the drop down menu
On the right-hand column labelled New (Only in Github), scroll down to click the apps to install. This will typically be:

_doortsy-app-manager.src_

Check the Publish box and Click the Execute Update in the bottom-right corner of the screen. When done syncing, the new apps should now appear in the IDE. If they ever change color, that indicates a new version is available.


#### Installing the Doortsy control Device Handlers
Go to "My Device Handlers" in the IDE
Under My Device Handlers Click on Update from Repo and select Doortsy_SmartThings from the drop-down
Check the box next to **doortsy-control** then check the Publish box and click Execute Update

That's it in the IDE... Just install "Doortsy Manager" from the Marketplace > MyApps under the mobile app.

When updates are available to the source code the Link color change from black in the IDE.

