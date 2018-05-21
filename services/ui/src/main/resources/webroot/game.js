var eb = new EventBus('/eventbus/');

eb.onopen = function () {
  eb.registerHandler('creategameobject', function (err, msg) {
    console.log('incomming EB message:');
    console.log(msg);
    $('#board').append('<div id="' + msg.body.id + '" style="' + msg.body.style + '">' + msg.body.text + '</div>');
  });
  eb.registerHandler('movegameobject', function (err, msg) {
    $('#' + msg.body.id).css('top', msg.body.y + 'px')
        .css('left', msg.body.x + 'px');
  });

  eb.send("init-session", "");
};


function init() {
}
