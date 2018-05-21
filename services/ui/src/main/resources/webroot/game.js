var eb = new EventBus('/eventbus/');

eb.onopen = function () {
  eb.registerHandler('creategameobject', function (err, msg) {
    console.log('incomming EB message:');
    console.log(msg);
    $('#board').append('<div id="' + msg.body.id + '" style="' + msg.body.style + '">' + msg.body.text + '</div>');
  });

  eb.send("init-session", "");
};


function init() {
}
