"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports["default"] = void 0;

var _redux = require("redux");

var _reactI18next = require("react-i18next");

var _styles = require("@material-ui/core/styles");

var _withPlugins = require("../extend/withPlugins");

var _WorkspaceControlPanel = require("../components/WorkspaceControlPanel");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 *
 * @param theme
 * @returns {{ctrlBtn: {margin: (number|string)},
 * drawer: {overflowX: string, height: string}}}
 */
var styles = function styles(theme) {
  var _branding;

  return {
    branding: (_branding = {
      display: 'flex',
      position: 'absolute'
    }, _defineProperty(_branding, theme.breakpoints.up('xs'), {
      display: 'none'
    }), _defineProperty(_branding, theme.breakpoints.up('sm'), {
      bottom: 0,
      display: 'block',
      "float": 'none',
      right: 'auto',
      width: '100%'
    }), _defineProperty(_branding, "right", 0), _branding),
    ctrlBtn: {
      margin: theme.spacing(1)
    },
    drawer: {
      overflowX: 'hidden'
    },
    root: _defineProperty({
      height: 64
    }, theme.breakpoints.up('sm'), {
      height: '100%',
      left: 0,
      right: 'auto',
      width: 64
    }),
    toolbar: _defineProperty({
      display: 'flex',
      justifyContent: 'space-between'
    }, theme.breakpoints.up('sm'), {
      flexDirection: 'column',
      justifyContent: 'flex-start',
      minHeight: 0
    }),
    wide: {
      width: 'auto'
    },
    workspaceButtons: _defineProperty({}, theme.breakpoints.up('sm'), {
      display: 'flex',
      flexDirection: 'column'
    })
  };
};

var enhance = (0, _redux.compose)((0, _reactI18next.withTranslation)(), (0, _styles.withStyles)(styles), (0, _withPlugins.withPlugins)('WorkspaceControlPanel') // further HOC go here
);

var _default = enhance(_WorkspaceControlPanel.WorkspaceControlPanel);

exports["default"] = _default;