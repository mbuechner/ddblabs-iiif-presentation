"use strict";(self.webpackChunkddbviewer=self.webpackChunkddbviewer||[]).push([[459],{459:(e,t,r)=>{r.r(t),r.d(t,{default:()=>y});var n=r(68238),o=r(41733),i=r(96540),u=r(18250);function c(e,t){for(var r=0;r<t.length;r++){var n=t[r];n.enumerable=n.enumerable||!1,n.configurable=!0,"value"in n&&(n.writable=!0),Object.defineProperty(e,a(n.key),n)}}function a(e){var t=function(e){if("object"!=typeof e||!e)return e;var t=e[Symbol.toPrimitive];if(void 0!==t){var r=t.call(e,"string");if("object"!=typeof r)return r;throw new TypeError("@@toPrimitive must return a primitive value.")}return String(e)}(e);return"symbol"==typeof t?t:t+""}function f(){try{var e=!Boolean.prototype.valueOf.call(Reflect.construct(Boolean,[],(function(){})))}catch(e){}return(f=function(){return!!e})()}function l(e){return l=Object.setPrototypeOf?Object.getPrototypeOf.bind():function(e){return e.__proto__||Object.getPrototypeOf(e)},l(e)}function p(e,t){return p=Object.setPrototypeOf?Object.setPrototypeOf.bind():function(e,t){return e.__proto__=t,e},p(e,t)}var s=(0,i.lazy)((function(){return Promise.resolve().then(r.bind(r,25367))})),b=function(e){function t(e){var r;return function(e,t){if(!(e instanceof t))throw new TypeError("Cannot call a class as a function")}(this,t),(r=function(e,t,r){return t=l(t),function(e,t){if(t&&("object"==typeof t||"function"==typeof t))return t;if(void 0!==t)throw new TypeError("Derived constructors may only return object or undefined");return function(e){if(void 0===e)throw new ReferenceError("this hasn't been initialised - super() hasn't been called");return e}(e)}(e,f()?Reflect.construct(t,r||[],l(e).constructor):t.apply(e,r))}(this,t,[e])).state={},r}return function(e,t){if("function"!=typeof t&&null!==t)throw new TypeError("Super expression must either be null or a function");e.prototype=Object.create(t&&t.prototype,{constructor:{value:e,writable:!0,configurable:!0}}),Object.defineProperty(e,"prototype",{writable:!1}),t&&p(e,t)}(t,e),r=t,o=[{key:"getDerivedStateFromError",value:function(e){return{hasError:!0}}}],(n=[{key:"render",value:function(){var e=this.props.windowId;return this.state.hasError?i.createElement(i.Fragment,null):i.createElement(i.Suspense,{fallback:i.createElement("div",null)},i.createElement(s,{windowId:e},i.createElement(u.default,{windowId:e})))}}])&&c(r.prototype,n),o&&c(r,o),Object.defineProperty(r,"prototype",{writable:!1}),r;var r,n,o}(i.Component);const y=(0,n.compose)((0,o.h)("WindowViewer"))(b)}}]);