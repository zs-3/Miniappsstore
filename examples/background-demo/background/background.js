function onBackgroundEvent(event) {
    var nowStr = new Date().toISOString();

    // Store timestamp in isolated mini-app storage
    Mist.call("storage.set", {
        key: "lastBackgroundRun",
        value: nowStr
    });

    // Send native system notification
    Mist.call("notifications.send", {
        title: "MistFox Background Task",
        body: "Task executed at " + nowStr
    });
}
