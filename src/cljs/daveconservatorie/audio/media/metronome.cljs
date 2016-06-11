(ns daveconservatorie.audio.media.metronome)

(def metro
  {"low" "data:audio/mp3;base64,SUQzBAAAAAAAa1RFTkMAAAAIAAADUkVBUEVSAFREUkMAAAAMAAADMjAwOS0wNy0xOQBUWFhYAAAAFgAAA3RpbWVfcmVmZXJlbmNlADcwNTU5AFRTU0UAAAAPAAADTGF2ZjU3LjM2LjEwMAAAAAAAAAAAAAAA//tAwAAAAAAAAAAAAAAAAAAAAAAASW5mbwAAAA8AAAAFAAAEygBRUVFRUVFRUVFRUVFRUVFRUVFRfX19fX19fX19fX19fX19fX19fX2oqKioqKioqKioqKioqKioqKioqNTU1NTU1NTU1NTU1NTU1NTU1NTU//////////////////////////8AAAAATGF2YzU3LjM5AAAAAAAAAAAAAAAAJAAAAAAAAAAABMolm39xAAAAAAD/+1DEAAAIvI9ftGMAAa2h7PcxIAKOgAAAAAA3W+7vf7JgAAAAhGPdxERERGPZMmAwGTJpkCBBCIgxAmTAYDAYWm0EyZO7u7uP4ggQIHP8o7gh9v/E4fd/u/wxJCAgCQACQQCAyIwwCBDLutge7eCyyz+L6/sISrmHbP+LmL9eTwkI5hiYmvJg4QQnCkixe8Z8ATAc44C6YlgiykvmJDBTAblkMLbqLz3/JMwWOsfJMr6//KqZ8cknkjBOpL//1Ey/rLhL/+dVGwIAAOAiUMuV//tSxASAi2C3LTzHgAFek2UVhL2qK4kheoAaCoHQ3TRj6jOnOxe0M3SK19SlxD9E1OQ8/Hb9yRt4ka8N6qqpAzQdBhF2OqEwMc3ao839pJ/ZXq1mo+cq219f4/9P/uADlxZuVLgqGRAYt08Qsaq0L00gRJBcDDIt2qAMihejJUvszQjTRFw3JoAVuFRktUs8uq/nTxmhgB3CzoTEfrdLvGX6e1xbe1ElDoPN5vFZ1mW6BIDwVdS10qde/////1oKA0AABIE/b3pWbUyCUDxuDzb/+1LECQCKmLsvRjz2QX+XJJmHrXjBJHUun51BNtSdbRwBknK0Xp4J+M+v8LX0m1OK8KWzTQyujdELorggXF45v9jX9TCiEpxJswp4lEgbPK//+W0/cj+kBEKyp952ZqU1Sq8rfhczolxQhRnpAYWCGeUafcAuUR8c4bbMTlUwFAtwatufrNoCteTleXIW4l73D0d0wYXb0mtlXAlA2GI7UVnHfr7/uKzlHg+UI///fp/x3yNv+z9dADlRU0ApLrJUsBhYFbZWqhkYlx4qgEHFDP/7UsQMgou4lSREYYaBRBIkpPYteBwdRFMnAChkBxIxB3/kP2tOtw8mNAIi45qX9ilDa7Z+UTEBDMhzoY8CD3LYPak4Eyph4eMKPgZr2EF/dv9UXR/01/3VKIDZDRU7yP85khj+nDSHirYsWI3iyytU5pzQ5uCAciaywvvE3ZdMx12GlRPMlARi2raNRbfbwbSoqGCJNA4s6sIuKNBg0x2+1J38bjvH/xb/oRmABIqiG6fRpSuSsL+bR+oAmyGHWqG+RtcnFvVkOqtxQ5XAwSSj//tSxBMAC0iRIMeZlECkgCMcEIxIkCaBLH7emuTKVxWOxMD8PbwwX6azVrp2rS8RToQSclnlYLERCorTtf6f8W9v3PtTs6fs+0AJSYlowsgCsQDIjATVNfxVIxLX4qK/7Isz/sdf//+LIYr//60MGIFDSkxBTUUzLjk5LjWqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqo="
   "high" "data:audio/mp3;base64,SUQzBAAAAAAAa1RFTkMAAAAIAAADUkVBUEVSAFREUkMAAAAMAAADMjAwOS0wNy0xOQBUWFhYAAAAFgAAA3RpbWVfcmVmZXJlbmNlADcwNTU5AFRTU0UAAAAPAAADTGF2ZjU3LjM2LjEwMAAAAAAAAAAAAAAA//tAwAAAAAAAAAAAAAAAAAAAAAAASW5mbwAAAA8AAAAFAAAEygBRUVFRUVFRUVFRUVFRUVFRUVFRfX19fX19fX19fX19fX19fX19fX2oqKioqKioqKioqKioqKioqKioqNTU1NTU1NTU1NTU1NTU1NTU1NTU//////////////////////////8AAAAATGF2YzU3LjM5AAAAAAAAAAAAAAAAJAAAAAAAAAAABMpk1D9lAAAAAAD/+1DEAAAJOJVftJMAAZ2bLPce8ADQgAAAAABP7e/PznOaAgFAICgkhc4iIiIiP///ZMAAAAAAAAAgQIEyZNO+0f9oggQyyBAgQiIiE7P/wQ5QMf8uD8H3//KHP+IAQ/wAADQoAAIBA7HoYCAerC6U2fFAfrG9+DBXI9Si3yCA7HsL8ZJ0p19mv/O0Lo1FSwp1Uvf/0PEympAgvYuI3//DH253OxzbYVoWLf//5jqsrX7eZ+/X/Nf//+/rrDrect2yd+qv//UqH8EIAGAiSwtV//tSxASAizCZMTz3gAF5nGVlh5W5e6W10fM4OEIaDMQ1WrKXhwTzisQ6042NMXG4veR6F0QkehLy0iMNEi7VrKOFcQPr53WmG9REiO6MtxYdNa1WVOP7esg3rAIK9QNgr////9KAjAC8CvJaHWVJhjJpfG1MQHBBDWZBaD1gMMOC2l1ay4OWcv/hrhrpjLUBsbGx1MzYm1HbBNVwhjj9enx4h2kGL+IXuZjHjhQriYgLCL/V7igfd73dnVAHM+a5txzC9wIABACwLP3L2OdPHrf/+1LEBoCLHOMrLDytyYEcJPGGDtglTJRVPzwjcjViWa1lihXXI7hrIZFxiF83eQD/JmVjUk3ihg99baInYV7OM/Nc4+X67JYm6PA045CBYeHAb/RkPSubYhjOn9xAQZq6CCQABOAmI++1nVzOhnJhIlGMFaBkHhqRKEZzcDanIRDi6ZimtROc3qrTPyoEaCt9d19SGNC8HwGlSP52KbyyTUgYMKvJ+69DE6Q0BmZmZryUSEDleVDhGqcz+fggLX/2qgAACACQCMlDVTgr4PgslP/7UsQHgIuwmyMk4YnBeZXk8YelqCFpHVqBOXqrdwjDgyjBr8+9N/uo7Yxp4tdf5lKwD6PvbymuV2dJx+tLCOeymeqbMgpAAPn4iw4tYQ4muace3JUfc4zMBSph6AX3f/+7/pBBAAQm1T9p2JPe3Vw7lBqfI4UCiXFc5U/Al22KISE9YTT9RGXVMd0JOQdWIVukeC3oZoAMzKVmf3eRVHATEnWFTsQXnSPqQ//nv8lBxoCqcSb1/3P2f2e+zbW31+/TAEAMAKAcnkPvtQ+gDQIy//tSxAeAi/CxJSewbcENkKKIwJrIiCUAlxGAJjU6eN3NCcUxOPzI/KlTNjT6hVFIBRFckyabqZcuXNLr03tzrwMEciGj+SFyMzoDF/JHhVhI4CEwTNC4EJrAghEiWD0bPT///07BJOXF21aXlkQhOH8PRCGkSCMIo9HBse48zR45hiXQ88znz2EmEJNF2XynUkRFGLRIhICkn4o+M/j06P/t/R1I0ts60ExBTUUzLjk5LjWqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqo="})
