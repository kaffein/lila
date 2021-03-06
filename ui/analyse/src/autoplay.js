var control = require('./control');
var partial = require('chessground').util.partial;
var m = require('mithril');

module.exports = function(ctrl) {

  var timeout;

  this.delay = null;

  var move = function() {
    if (control.canGoForward(ctrl)) {
      var p = ctrl.vm.path;
      p[p.length - 1].ply++;
      ctrl.jump(p);
      m.redraw();
      return true;
    }
    this.stop();
    m.redraw();
    return false;
  }.bind(this);

  var nextDelay = function() {
    if (this.delay === true) {
      // in a variation
      if (ctrl.vm.path.length > 1) return 2000;
      return (ctrl.data.game.moveTimes[ctrl.vm.path[0].ply] * 100) || 2000;
    }
    return this.delay;
  }.bind(this);

  var schedule = function() {
    timeout = setTimeout(function() {
      if (move()) schedule();
    }, nextDelay());
  }.bind(this);

  var start = function(delay) {
    this.delay = delay;
    this.stop();
    schedule();
  }.bind(this);

  this.stop = function() {
    if (timeout) {
      clearTimeout(timeout);
      timeout = null;
    }
  }.bind(this);

  this.toggle = function(delay) {
    if (this.active(delay)) this.stop();
    else {
      if (!this.active()) move();
      start(delay);
    }
  }.bind(this);

  this.active = function(delay) {
    return (!delay || delay === this.delay) && !!timeout;
  }.bind(this);
};
